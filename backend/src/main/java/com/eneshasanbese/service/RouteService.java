package com.eneshasanbese.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.dto.RouteDto;
import com.eneshasanbese.dto.RouteStopDto;
import com.eneshasanbese.dto.ServiceDto;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.enums.Shift;
import com.eneshasanbese.util.GeoUtils;
import com.eneshasanbese.util.OrOpt;
import com.eneshasanbese.util.RoadMatrix;

/**
 * Tek bir servisin güzergâhını kurar.
 *
 * <p>
 * Sabah rota şoförün ev adresinden başlar, atanmış işçileri sırayla toplar ve
 * ofiste biter; akşam uçlar yer değiştirir (sabit garaj yok — seed dosyasındaki
 * senaryo bu). Sıralama en-yakın-komşu ile kurulur, ardından 2-opt ve Or-opt
 * dönüşümlü çalıştırılarak iyileştirilir; iki uç nokta sabit tutulur.
 *
 * <p>
 * <b>Mesafe her yerde gerçek yol mesafesidir</b>, kaynağı iki türlü:
 * <ul>
 * <li><i>Servis başına matris</i> — {@link #build} OSRM'in {@code /table}
 * servisinden servis başına tek istekle N×N mesafe alır.
 * Haritaya çizilen rota da bu sıra üzerinden {@code /route} ile çekilir.</li>
 * <li><i>Sistem çapında tek matris</i> — {@link #buildRoadMatrix} bütün
 * noktaları (ofis + şoförler + işçiler) tek istekte çıkarır ve
 * {@link #estimateLoad} bunun üzerinde çalışır. Toplu dağıtım on binlerce
 * değerlendirme yaptığı için orada ağ isteği yapılamaz; matris o değerlendirmeyi
 * dizi okumasına indiriyor.</li>
 * </ul>
 * OSRM ulaşılamadığında ikisi de kuş uçuşu × karayolu çarpanı tahminine düşer:
 * uygulama çalışmaya devam eder, yalnızca körleşir.
 *
 * <p>
 * <b>Süre</b> ise OSRM'in boş yol süresinin, o koridorun İBB verisinden çıkan
 * tıkanıklık çarpanıyla ölçeklenmesiyle bulunur; ayrıntısı
 * {@link #legDurationsMinutes} ve README'deki "Süre modeli" başlığında.
 */
@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int LOCAL_SEARCH_MAX_PASSES = 50;
    /** 2-opt ile Or-opt turlarının birbirini besleme sayısı. */
    private static final int IMPROVE_MAX_ROUNDS = 10;
    /** Bir hamlenin kabul edilmesi için gereken en az kazanç (dakika). */
    private static final double IMPROVEMENT_EPSILON = 1e-9;
    /** Rota önbelleğinin üst sınırı; aşılınca tamamen boşaltılır. */
    private static final int ROUTE_CACHE_LIMIT = 250;
    /** Bacak boyunca tıkanıklık çarpanının kaç noktadan örnekleneceği. */
    private static final int FACTOR_SAMPLES = 9;
    /** Kalibrasyonda çekilecek örnek zincir sayısı. */
    private static final int CALIBRATION_CHAINS = 8;
    /** Her örnek zincirdeki durak sayısı — tipik bir servise yakın. */
    private static final int CHAIN_LENGTH = 10;
    /** Sabit tohum: kalibrasyon da dağıtım gibi tekrarlanabilir olmalı. */
    private static final long CALIBRATION_SEED = 20250101L;

    private final TrafficSpeedService trafficSpeedService;
    private final OsrmClient osrmClient;
    private final RouteSettings settings;

    /**
     * Hesaplanmış rotalar. Arayüz 5 saniyede bir bütün servisleri sorguluyor;
     * önbellek olmadan her poll 10 servis × (1 {@code /table} + 1 {@code /route})
     * isteği demek olurdu. Anahtar girdinin tamamını (servis, şoför konumu,
     * işçilerin id ve koordinatları) kapsadığı için bayat sonuç dönmesi mümkün
     * değil: atama ya da adres değişince anahtar da değişir.
     */
    private final Map<String, RouteResult> routeCache = new ConcurrentHashMap<>();

    public RouteService(
            TrafficSpeedService trafficSpeedService,
            OsrmClient osrmClient,
            RouteSettings settings) {
        this.trafficSpeedService = trafficSpeedService;
        this.osrmClient = osrmClient;
        this.settings = settings;
    }

    /**
     * Hesaplanmış rota.
     *
     * @param geometry yol çizgisi ([lat, lon] noktaları); OSRM yoksa null olur ve
     *                 arayüz durakları düz çizgiyle birleştirir
     */
    public record RouteResult(
            List<RouteStopDto> stops,
            List<double[]> geometry,
            double totalKm,
            double totalMinutes,
            /** En uzun süre araçta kalan yolcunun süresi. Şoför sayılmaz. */
            int maxRideMinutes,
            /** Sabah: kalkış saati. Akşam: ofis kalkışı (sabit). */
            String startTime,
            /** Sabah: ofise varış. Akşam: son yolcunun indiği saat. */
            String endTime) {
    }

    public RouteResult build(ServiceVehicle vehicle, Driver driver, List<Worker> workers, Shift shift) {
        String key = shift.name() + "|" + cacheKey(vehicle, driver, workers);

        RouteResult cached = routeCache.get(key);
        if (cached != null) {
            return cached;
        }

        RouteResult result = compute(vehicle, driver, workers, shift);

        if (routeCache.size() >= ROUTE_CACHE_LIMIT) {
            routeCache.clear();
        }
        routeCache.put(key, result);

        return result;
    }

    /**
     * Bir servisin bir seferdeki yükü.
     *
     * @param totalMinutes turun tamamı — şoförün evden çıkışından ofise (akşam
     *                     ofisten evine) varışına kadar
     * @param maxRideMinutes en uzun süre araçta kalan <b>yolcunun</b> süresi;
     *                     90 dakika kuralının ölçtüğü şey budur, şoför sayılmaz
     */
    public record LoadEstimate(double totalMinutes, double maxRideMinutes) {
    }

    /**
     * Toplu dağıtımda kullanılan hafif yük tahmini: en-yakın-komşu turu, ardından
     * yalnızca Or-opt ile kısa bir düzeltme.
     *
     * <p>
     * <b>Neden {@link #build} değil de bu:</b> {@link AssignmentService} bunu on
     * binlerce kez çağırıyor. Buraya ağ isteği koymak dağıtımı dakikalar sürecek
     * hale getirirdi; onun yerine {@code matrix} önceden çıkarılmış tek yol
     * matrisini taşıyor ve hesap dizi okumasına iniyor. {@code matrix} null ise
     * (OSRM kapalı) eski kuş uçuşu tahmine düşülür.
     *
     * <p>
     * <b>Neden 2-opt yok:</b> tahminin işi sıralamayı bulmak değil, iki
     * alternatif <em>yolcu kümesini</em> karşılaştırmak. Or-opt asimetrik
     * matriste doğru çalışan ucuz hamle; 2-opt hem pahalı hem bu matriste tek
     * başına yanıltıcı. Yine de düz en-yakın-komşu yetmiyor: kural cezası
     * {@code maxRideMinutes} üzerinden işlediği için kötü bir tur, olmayan bir
     * ihlal uydurup atamayı yanlış yönlendirirdi.
     */
    public LoadEstimate estimateLoad(Driver driver, List<Worker> workers, RoadMatrix matrix, Shift shift) {
        if (workers.isEmpty()) {
            return new LoadEstimate(0, 0);
        }

        double[][] nodes = nodes(shift, driver, workers);
        int endNode = endNode(nodes);

        LegCost precomputed = tableCost(matrix, shift, driver, workers);
        LegCost cost = precomputed != null
                ? precomputed
                : costFunction(nodes, sliceOf(matrix, shift, driver, workers), timeSlotOf(shift));

        List<Integer> order = nearestNeighbour(START_NODE, workerNodes(workers.size()), cost);
        orOpt(order, endNode, cost);

        double total = tourMinutes(order, START_NODE, endNode, cost);

        // Sabah ilk binen, akşam en son inen en uzun yolculuğu yapar; ikisi de
        // turun tamamından bir uç bacağın çıkarılmasıyla bulunur.
        double maxRide = shift == Shift.SABAH
                ? total - cost.minutes(START_NODE, order.getFirst()) - settings.getBoardingMinutes()
                : total - cost.minutes(order.getLast(), endNode);

        return new LoadEstimate(total, Math.max(0, maxRide));
    }

    /**
     * Bütün noktaların birbirine yol mesafesi — tek {@code /table} isteği.
     *
     * <p>
     * Nokta sayısı ofis + şoförler + işçiler kadar (bu veri setinde 114). OSRM'in
     * {@code --max-table-size} sınırı docker-compose'da 2000; kurumun personeli
     * birkaç katına çıksa bile tek istek yetmeye devam eder.
     *
     * @return matris; OSRM kapalı ya da ulaşılamazsa boş
     */
    public Optional<RoadMatrix> buildRoadMatrix(Collection<Driver> drivers, Collection<Worker> workers) {
        List<double[]> points = new ArrayList<>();
        points.add(new double[] { settings.getOfficeLat(), settings.getOfficeLon() });

        Map<Long, Integer> driverIndex = new LinkedHashMap<>();
        for (Driver driver : drivers) {
            if (driver == null || driverIndex.containsKey(driver.getId())) {
                continue;
            }
            driverIndex.put(driver.getId(), points.size());
            points.add(new double[] { driver.getLatitude(), driver.getLongitude() });
        }

        Map<Long, Integer> workerIndex = new LinkedHashMap<>();
        for (Worker worker : workers) {
            if (worker == null || workerIndex.containsKey(worker.getId())) {
                continue;
            }
            workerIndex.put(worker.getId(), points.size());
            points.add(new double[] { worker.getLatitude(), worker.getLongitude() });
        }

        long started = System.currentTimeMillis();
        Optional<OsrmClient.Matrix> table = osrmClient.matrix(points);

        if (table.isEmpty()) {
            log.warn("Yol matrisi alınamadı ({} nokta); dağıtım kuş uçuşu mesafeyle yapılacak.", points.size());
            return Optional.empty();
        }

        RoadMatrix matrix = new RoadMatrix(
                workerIndex, driverIndex, table.get().meters(), table.get().seconds());

        for (Shift shift : Shift.values()) {
            String timeSlot = timeSlotOf(shift);
            matrix.putMinutes(timeSlot, minutesTable(points, table.get(), timeSlot));
        }

        log.info("Yol matrisi hazır: {} nokta, {} ms.", points.size(), System.currentTimeMillis() - started);
        return Optional.of(matrix);
    }

    /**
     * Genel matristen tek bir servisin düğümlerine karşılık gelen küçük matrisi
     * çıkarır. Düğüm sırası {@link #nodes} ile birebir aynı olmak zorunda.
     */
    /**
     * Önceden hesaplanmış dakika tablosunu okuyan maliyet — atama aramasının
     * sıcak yolu. Servisin düğümleri genel matristeki numaralarına eşlenir ve
     * hesap tek dizi okumasına iner.
     *
     * @return tablo yoksa ya da noktalardan biri matriste değilse null; çağıran
     *         taraf o zaman bacakları tek tek hesaplar
     */
    private LegCost tableCost(RoadMatrix matrix, Shift shift, Driver driver, List<Worker> workers) {
        if (matrix == null) {
            return null;
        }

        double[][] minutes = matrix.minutes(timeSlotOf(shift));
        if (minutes == null) {
            return null;
        }

        int[] global = globalNodes(matrix, shift, driver, workers);
        for (int node : global) {
            if (node == RoadMatrix.UNKNOWN) {
                return null;
            }
        }

        return (from, to) -> minutes[global[from]][global[to]];
    }

    /**
     * Servisin düğümlerinin genel matristeki numaraları. Sıra {@link #nodes} ile
     * birebir aynı olmak zorunda: 0 = kalkış, 1..N = işçiler, N+1 = varış.
     */
    private int[] globalNodes(RoadMatrix matrix, Shift shift, Driver driver, List<Worker> workers) {
        int size = workers.size() + 2;
        int[] global = new int[size];

        int home = driver == null ? RoadMatrix.OFFICE : matrix.driver(driver.getId());
        global[START_NODE] = shift == Shift.SABAH ? home : RoadMatrix.OFFICE;
        global[size - 1] = shift == Shift.SABAH ? RoadMatrix.OFFICE : home;

        for (int i = 0; i < workers.size(); i++) {
            global[i + 1] = matrix.worker(workers.get(i).getId());
        }

        return global;
    }

    private OsrmClient.Matrix sliceOf(RoadMatrix matrix, Shift shift, Driver driver, List<Worker> workers) {
        if (matrix == null) {
            return null;
        }

        int size = workers.size() + 2;
        int[] global = globalNodes(matrix, shift, driver, workers);

        double[][] meters = new double[size][size];
        double[][] seconds = new double[size][size];

        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                // Tanınmayan nokta NaN döner; maliyet o bacak için tahmine düşer.
                meters[from][to] = matrix.meters(global[from], global[to]);
                seconds[from][to] = matrix.seconds(global[from], global[to]);
            }
        }

        return new OsrmClient.Matrix(meters, seconds);
    }

    /**
     * Bütün ikililerin sürüş dakikası — matris kurulurken bir kez.
     *
     * <p>
     * Süre modeli rota hesabındakiyle aynı: OSRM'in boş yol süresi × bölgesel
     * tıkanıklık çarpanı. Fark yalnızca çarpanın nereden örneklendiği. Gerçek
     * rota çizgisi boyunca örnekliyor, burada henüz çizgi yok; iki nokta
     * arasındaki doğru üzerinde {@link #FACTOR_SAMPLES} nokta alınıyor.
     */
    private double[][] minutesTable(List<double[]> points, OsrmClient.Matrix table, String timeSlot) {
        int size = points.size();
        double[][] minutes = new double[size][size];
        boolean congestionKnown = trafficSpeedService.hasFreeFlowData();

        double[][] seconds = table.seconds();
        double[][] meters = table.meters();
        double calibration = congestionKnown ? estimatorCalibration(points, table, timeSlot) : 1.0;

        for (int from = 0; from < size; from++) {
            double[] start = points.get(from);

            for (int to = 0; to < size; to++) {
                if (from == to) {
                    continue;
                }
                double[] end = points.get(to);

                double freeFlow = seconds == null ? Double.NaN : seconds[from][to];
                if (congestionKnown && Double.isFinite(freeFlow) && freeFlow > 0) {
                    minutes[from][to] = freeFlow / 60.0
                            * sampledFactorAlongLine(start, end, timeSlot) * calibration;
                    continue;
                }

                double km = Double.isFinite(meters[from][to])
                        ? meters[from][to] / 1000.0
                        : straightRoadKm(start[0], start[1], end[0], end[1]);
                minutes[from][to] = legMinutes(start[0], start[1], end[0], end[1], km, timeSlot);
            }
        }

        return minutes;
    }

    /**
     * Doğru üzerinde örneklenen tıkanıklık çarpanının, <b>gerçek yol</b> üzerinde
     * örneklenene oranı.
     *
     * <p>
     * <b>Neden gerekiyor.</b> İki ev arasındaki doğru, aracın gerçekte gittiği
     * yol değil. Araç ana arterden geçiyor, doğru ise ara mahallelerin üstünden
     * geçiyor. Akşam zirvesinde bu fark büyük: tıkanıklık arterlerde toplanıyor,
     * mahalle sokakları neredeyse boş. Ölçüldüğünde tahmin, gerçek rotanın akşam
     * süresini sistematik olarak <b>%12 düşük</b> veriyordu — yani atama, kuralı
     * çiğnemediğini sanıyordu.
     *
     * <p>
     * <b>Neden elle bir sayı değil.</b> Bu oran şehre, veriye ve zaman dilimine
     * göre değişir; kafadan bir çarpan koymak modelin geri kalanının dayandığı
     * "ölçtüğümüzü kullan" ilkesini bozardı. Onun yerine birkaç gerçek rota
     * çekilip iki örnekleme karşılaştırılıyor ve <b>medyan</b> alınıyor (ortalama
     * değil: tek bir uç bacak oranı kaydırmasın).
     *
     * <p>
     * Örnek çiftler gerçek bacaklara benzesin diye rastgele değil, birbirine
     * yakın noktalardan seçiliyor — tur bacakları da kısa. Tohum sabit, yani
     * kalibrasyon tekrarlanabilir.
     */
    private double estimatorCalibration(List<double[]> points, OsrmClient.Matrix table, String timeSlot) {
        if (table.seconds() == null || points.size() < CHAIN_LENGTH) {
            return 1.0;
        }

        List<Double> ratios = new ArrayList<>();
        Random random = new Random(CALIBRATION_SEED);

        for (int attempt = 0; attempt < CALIBRATION_CHAINS; attempt++) {
            List<Integer> chain = sampleChain(table.meters(), random);
            List<double[]> chainPoints = chain.stream().map(points::get).toList();

            Optional<OsrmClient.Route> route = osrmClient.route(chainPoints);
            if (route.isEmpty() || route.get().legs().size() != chain.size() - 1) {
                continue;
            }

            List<double[]> geometry = route.get().geometry();
            int[] boundaries = geometryBoundaries(chainPoints, geometry);

            double truth = 0;
            double estimate = 0;

            for (int i = 0; i < chain.size() - 1; i++) {
                truth += route.get().legs().get(i).durationSeconds() / 60.0
                        * sampledCongestionFactor(geometry, boundaries[i], boundaries[i + 1], timeSlot);

                estimate += table.seconds()[chain.get(i)][chain.get(i + 1)] / 60.0
                        * sampledFactorAlongLine(chainPoints.get(i), chainPoints.get(i + 1), timeSlot);
            }

            if (truth > 0 && estimate > 0) {
                ratios.add(truth / estimate);
            }
        }

        if (ratios.size() * 2 < CALIBRATION_CHAINS) {
            log.warn("Kalibrasyon için yeterli rota çekilemedi ({} zincir); düzeltme uygulanmıyor.",
                    ratios.size());
            return 1.0;
        }

        Collections.sort(ratios);
        double median = ratios.get(ratios.size() / 2);

        log.info("Tahmin kalibrasyonu [{}]: {} zincirde medyan oran {}.",
                timeSlot, ratios.size(), round2(median));
        return median;
    }

    /**
     * Gerçek bir tur bacağına benzeyen nokta zinciri: rastgele bir noktadan
     * başlayıp her adımda en yakın ziyaret edilmemiş komşuya gidilir. Rastgele
     * çiftler işe yaramazdı — şehrin iki ucu arasındaki tek uzun bacakta ara
     * durak kısıtı görünmez, tur bacakları ise kısa ve sık.
     */
    private List<Integer> sampleChain(double[][] meters, Random random) {
        List<Integer> chain = new ArrayList<>();
        chain.add(random.nextInt(meters.length));

        while (chain.size() < CHAIN_LENGTH) {
            int current = chain.getLast();
            int best = -1;
            double bestMeters = Double.MAX_VALUE;

            for (int next = 0; next < meters.length; next++) {
                if (chain.contains(next) || !Double.isFinite(meters[current][next])) {
                    continue;
                }
                if (meters[current][next] < bestMeters) {
                    bestMeters = meters[current][next];
                    best = next;
                }
            }

            if (best < 0) {
                break;
            }
            chain.add(best);
        }

        return chain;
    }

    /**
     * İki nokta arasındaki doğru üzerinde eşit aralıklı örneklenen tıkanıklık
     * çarpanının ortalaması. Uç noktalar yarım ağırlıkla girer (yamuk kuralı):
     * yolun büyük kısmı arada geçiyor ve iki ev, genellikle geçtikleri ana
     * arterden daha sakin hücrelerde.
     */
    private double sampledFactorAlongLine(double[] start, double[] end, String timeSlot) {
        double total = 0;
        double weightSum = 0;

        for (int i = 0; i < FACTOR_SAMPLES; i++) {
            double t = (double) i / (FACTOR_SAMPLES - 1);
            double weight = (i == 0 || i == FACTOR_SAMPLES - 1) ? 0.5 : 1.0;

            total += weight * trafficSpeedService.congestionFactor(
                    start[0] + (end[0] - start[0]) * t,
                    start[1] + (end[1] - start[1]) * t,
                    timeSlot);
            weightSum += weight;
        }

        return total / weightSum;
    }

    static String timeSlotOf(Shift shift) {
        return shift == Shift.SABAH
                ? TrafficSpeedService.SLOT_MORNING
                : TrafficSpeedService.SLOT_EVENING;
    }

    public ServiceDto toService(
            ServiceVehicle vehicle, RouteResult result, int kisiSayisi, Shift shift) {

        int durationMinutes = (int) Math.round(result.totalMinutes());
        int limit = settings.getMaxRideMinutes();

        return new ServiceDto(
                vehicle.getId(),
                vehicle.getPlateNumber(),
                shift.label(),
                kisiSayisi,
                settings.getMinCapacity(),
                vehicle.getCapacity(),
                round1(result.totalKm()),
                durationMinutes,
                result.startTime(),
                result.endTime(),
                result.maxRideMinutes(),
                limit,
                result.maxRideMinutes() > limit);
    }

    public RouteDto toRoute(RouteResult result) {
        return new RouteDto(result.stops(), result.geometry());
    }

    /** Ofiste 08:00'de olunacak şekilde geriye sayılan kalkış saati. */
    public String departureTime(int durationMinutes) {
        return settings.arrivalAt().minusMinutes(durationMinutes).format(HHMM);
    }

    // ---------------------------------------------------------------- kurgu

    private RouteResult compute(ServiceVehicle vehicle, Driver driver, List<Worker> workers, Shift shift) {
        String timeSlot = timeSlotOf(shift);

        double[][] nodes = nodes(shift, driver, workers);
        int endNode = endNode(nodes);

        // Sıralamanın tamamı bu tek matris üzerinden yapılır. Akşam sırası
        // sabahın tersi olarak türetilmiyor: matris asimetrik ve akşam
        // tıkanıklığı her koridorda aynı oranda artmıyor.
        OsrmClient.Matrix roadMatrix = roadMatrix(nodes);
        LegCost cost = costFunction(nodes, roadMatrix, timeSlot);

        List<Integer> order = nearestNeighbour(START_NODE, workerNodes(workers.size()), cost);
        improve(order, endNode, cost);

        List<Worker> ordered = order.stream().map(node -> workers.get(node - 1)).toList();

        List<Integer> sequence = new ArrayList<>(order.size() + 2);
        sequence.add(START_NODE);
        sequence.addAll(order);
        sequence.add(endNode);

        List<double[]> points = sequence.stream().map(node -> nodes[node]).toList();

        // Sıra kesinleşti; çizgiyi ve o sıranın kesin bacaklarını almak için tek
        // bir /route isteği.
        Optional<OsrmClient.Route> osrmRoute = osrmClient.route(points);
        List<double[]> geometry = osrmRoute.map(OsrmClient.Route::geometry).orElse(null);

        List<Double> legKm = legDistancesKm(
                nodes, roadMatrix == null ? null : roadMatrix.meters(), sequence, osrmRoute);
        List<Double> legMinutes = legDurationsMinutes(points, geometry, legKm, osrmRoute, timeSlot);
        List<Double> legVariation = legVariations(points, geometry, timeSlot);

        return assemble(vehicle, driver, ordered, points, legKm, legMinutes, legVariation, geometry, shift);
    }

    /**
     * Her bacağın hız oynaklığı (varyasyon katsayısı) — varış penceresinin
     * genişliği buradan geliyor.
     *
     * <p>
     * Geometri varsa çizgi boyunca mesafe-ağırlıklı ortalama alınır; yoksa
     * bacağın iki ucuna bakılır. Hiç ölçüm yoksa yapılandırılmış varsayılana
     * düşülür — pencereyi tamamen kapatmak, sahip olmadığımız bir kesinliği
     * iddia etmek olurdu.
     */
    private List<Double> legVariations(List<double[]> points, List<double[]> geometry, String timeSlot) {
        int legCount = points.size() - 1;
        List<Double> variations = new ArrayList<>(legCount);

        int[] boundaries = geometry != null && geometry.size() >= 2
                ? geometryBoundaries(points, geometry)
                : null;

        for (int i = 0; i < legCount; i++) {
            double variation = boundaries != null
                    ? sampledVariation(geometry, boundaries[i], boundaries[i + 1], timeSlot)
                    : 0;

            if (variation <= 0) {
                double[] from = points.get(i);
                double[] to = points.get(i + 1);
                variation = (trafficSpeedService.speedVariation(from[0], from[1], timeSlot)
                        + trafficSpeedService.speedVariation(to[0], to[1], timeSlot)) / 2;
            }

            variations.add(variation > 0 ? variation : settings.getDefaultVariation());
        }

        return variations;
    }

    /** Çizginin bir parçası boyunca mesafe-ağırlıklı ortalama oynaklık. */
    private double sampledVariation(List<double[]> geometry, int from, int to, String timeSlot) {
        double km = 0;
        double weighted = 0;

        for (int i = from; i < to; i++) {
            double[] start = geometry.get(i);
            double[] end = geometry.get(i + 1);

            double segment = GeoUtils.haversineKm(start[0], start[1], end[0], end[1]);
            if (segment <= 0) {
                continue;
            }

            double variation = trafficSpeedService.speedVariation(
                    (start[0] + end[0]) / 2, (start[1] + end[1]) / 2, timeSlot);

            km += segment;
            weighted += segment * variation;
        }

        return km > 0 ? weighted / km : 0;
    }

    /**
     * Bacak mesafeleri, güvenilirlik sırasıyla: seçilen sıra için {@code /route}
     * bacakları → {@code /table} matrisi → kuş uçuşu × karayolu çarpanı.
     */
    private List<Double> legDistancesKm(
            double[][] nodes,
            double[][] roadMeters,
            List<Integer> sequence,
            Optional<OsrmClient.Route> osrmRoute) {

        int legCount = sequence.size() - 1;

        if (osrmRoute.isPresent() && osrmRoute.get().legs().size() == legCount) {
            return osrmRoute.get().legs().stream()
                    .map(leg -> leg.distanceMeters() / 1000.0)
                    .toList();
        }

        List<Double> distances = new ArrayList<>(legCount);
        for (int i = 0; i < legCount; i++) {
            distances.add(legKm(nodes, roadMeters, sequence.get(i), sequence.get(i + 1)));
        }
        return distances;
    }

    /**
     * Bacak süreleri. Üç kademe var, en iyisinden en kabasına:
     *
     * <ol>
     * <li><b>OSRM süresi × tıkanıklık çarpanı</b> — tercih edilen yol. OSRM'in
     * süresi yol sınıfını, hız limitini ve dönüş cezalarını zaten biliyor; İBB
     * verisi ise yalnızca "bu bölge kendi normalinden ne kadar yavaş" sorusuna
     * cevap veriyor. Her kaynak yapabildiği işte kullanılıyor.</li>
     * <li><b>Mesafe / örneklenmiş hız</b> — serbest akış verisi yoksa. Süre yine
     * çizgi boyunca örnekleniyor ama mutlak hız İBB'den geliyor.</li>
     * <li><b>Mesafe / iki uç hızı</b> — geometri yoksa (OSRM kapalı).</li>
     * </ol>
     *
     * <p>
     * <b>Neden 1. kademe gerekli:</b> İBB verisi konumla anahtarlanmış, yolla
     * değil. Ölçülmemiş bir ara sokak komşu arterin hızını miras alıyordu ve
     * ölçümde 10 servisin 3'ü <em>boş yoldan hızlı</em> süre üretiyordu — fiziksel
     * olarak imkânsız. Tabanı OSRM'e taşımak bunu yapısal olarak engelliyor:
     * çarpan 1'in altına inemediği için süre serbest akışın altına düşemez.
     *
     * <p>
     * Bilinen sınır: çarpan hücre başına tek sayı, yani aynı hücredeki otoyol ile
     * ara sokak aynı oranı paylaşıyor. Hata sınırlı (çarpan aralığı ~1.0–1.4) ve
     * yönü güvenli tarafta — ara sokakları fazla cezalandırıp erken varış
     * tahmin ediyoruz.
     */
    private List<Double> legDurationsMinutes(
            List<double[]> points,
            List<double[]> geometry,
            List<Double> legKm,
            Optional<OsrmClient.Route> osrmRoute,
            String timeSlot) {

        // 3. kademe: iki uç hızı. Diğerleri bunun üzerine yazar.
        List<Double> minutes = new ArrayList<>(legKm.size());
        for (int i = 0; i < legKm.size(); i++) {
            double[] from = points.get(i);
            double[] to = points.get(i + 1);
            minutes.add(legMinutes(from[0], from[1], to[0], to[1], legKm.get(i), timeSlot));
        }

        if (geometry == null || geometry.size() < 2) {
            return minutes;
        }

        int[] boundaries = geometryBoundaries(points, geometry);

        List<OsrmClient.Leg> legs = osrmRoute.map(OsrmClient.Route::legs).orElse(List.of());
        boolean useOsrmDuration = legs.size() == legKm.size() && trafficSpeedService.hasFreeFlowData();

        for (int i = 0; i < legKm.size(); i++) {
            if (useOsrmDuration) {
                // 1. kademe
                double freeFlowMinutes = legs.get(i).durationSeconds() / 60.0;
                double factor = sampledCongestionFactor(geometry, boundaries[i], boundaries[i + 1], timeSlot);
                if (freeFlowMinutes > 0 && factor > 0) {
                    minutes.set(i, freeFlowMinutes * factor);
                    continue;
                }
            }

            // 2. kademe
            double speed = sampledSpeedKmh(geometry, boundaries[i], boundaries[i + 1], timeSlot);
            if (speed > 1) {
                minutes.set(i, legKm.get(i) / speed * 60);
            }
        }

        return minutes;
    }

    /**
     * Çizginin bir parçası boyunca mesafe-ağırlıklı ortalama tıkanıklık çarpanı.
     *
     * <p>
     * Ağırlık mesafe: uzun kesimler sonucu hak ettiği kadar etkiler. Parça yoksa
     * 0 döner ve çağıran taraf bir alt kademeye iner.
     */
    private double sampledCongestionFactor(List<double[]> geometry, int from, int to, String timeSlot) {
        double km = 0;
        double weighted = 0;

        for (int i = from; i < to; i++) {
            double[] start = geometry.get(i);
            double[] end = geometry.get(i + 1);

            double segment = GeoUtils.haversineKm(start[0], start[1], end[0], end[1]);
            if (segment <= 0) {
                continue;
            }

            double factor = trafficSpeedService.congestionFactor(
                    (start[0] + end[0]) / 2,
                    (start[1] + end[1]) / 2,
                    timeSlot);

            km += segment;
            weighted += segment * factor;
        }

        return km > 0 ? weighted / km : 0;
    }

    /**
     * Her durağın rota çizgisi üzerindeki karşılığı — bacakların çizgideki
     * sınırları.
     *
     * <p>
     * Arama ileriye doğru ilerler: rota aynı noktanın yakınından iki kez geçerse
     * ikinci durak geriye eşleşmesin diye. Son durak her zaman çizginin sonudur.
     */
    private int[] geometryBoundaries(List<double[]> points, List<double[]> geometry) {
        int[] boundaries = new int[points.size()];
        int cursor = 0;

        for (int i = 1; i < points.size() - 1; i++) {
            double[] stop = points.get(i);
            int best = cursor;
            double bestDistance = Double.MAX_VALUE;

            for (int g = cursor; g < geometry.size(); g++) {
                double[] point = geometry.get(g);
                double distance = GeoUtils.haversineKm(stop[0], stop[1], point[0], point[1]);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = g;
                }
            }

            boundaries[i] = best;
            cursor = best;
        }

        boundaries[points.size() - 1] = geometry.size() - 1;
        return boundaries;
    }

    /**
     * Çizginin bir parçası boyunca mesafe-ağırlıklı ortalama hız (km/sa).
     *
     * <p>
     * Her küçük parçanın orta noktası kendi geohash hücresinin hızıyla
     * değerlendirilip harmonik ortalama alınıyor — yani uzun ve yavaş kesimler
     * sonucu hak ettiği kadar aşağı çekiyor. Parça yoksa 0 döner, çağıran taraf
     * iki uçlu hesaba düşer.
     */
    private double sampledSpeedKmh(List<double[]> geometry, int from, int to, String timeSlot) {
        double km = 0;
        double hours = 0;

        for (int i = from; i < to; i++) {
            double[] start = geometry.get(i);
            double[] end = geometry.get(i + 1);

            double segment = GeoUtils.haversineKm(start[0], start[1], end[0], end[1]);
            if (segment <= 0) {
                continue;
            }

            double speed = trafficSpeedService.speedKmh(
                    (start[0] + end[0]) / 2,
                    (start[1] + end[1]) / 2,
                    timeSlot);

            km += segment;
            hours += segment / speed;
        }

        return hours > 0 ? km / hours : 0;
    }

    /**
     * Durakları, saatleri ve yolculuk sürelerini kurar.
     *
     * <p>
     * <b>Zaman çapası sefere göre değişiyor.</b> Sabah <i>varış</i> sabittir:
     * ofiste 08:00'de olunacak, kalkış geriye sayılıyor. Akşam <i>kalkış</i>
     * sabittir: 17:30'da ofisten çıkılıyor, varışlar ileriye sayılıyor. Her iki
     * durumda da araç planlanan saatte yola çıkar ve belirsizlik yol aldıkça
     * birikir.
     *
     * <p>
     * <b>Varış penceresi.</b> Tek bir dakika yazmak, sahip olmadığımız bir
     * kesinliği iddia etmek olurdu. Pencerenin genişliği İBB verisinin kendi
     * günden güne oynaklığından geliyor (sabah medyanı %6.2). Sapmalar
     * <em>doğrusal</em> toplanıyor, karekök değil: İstanbul'da tıkanıklık şehir
     * çapında birlikte hareket eder, bağımsız varsaymak pencereyi gerçekçi
     * olmayacak kadar daraltırdı.
     *
     * <p>
     * <b>Yolculuk süresi</b> yolcunun araçta geçirdiği süredir: sabah kapısına
     * gelindiği andan ofise varışa, akşam ofisten kalkıştan kapısında indiği ana
     * kadar. Şoförün kendi süresi sayılmaz — bütün turu yapan o, ama bu onun işi.
     */
    private RouteResult assemble(
            ServiceVehicle vehicle,
            Driver driver,
            List<Worker> ordered,
            List<double[]> points,
            List<Double> legKm,
            List<Double> legMinutes,
            List<Double> legVariation,
            List<double[]> geometry,
            Shift shift) {

        String driverLabel = driver != null
                ? driver.getName() + " " + driver.getSurname()
                : "Şoför";

        // Kalkış ve varış etiketleri sefere göre yer değiştiriyor.
        String startLabel = shift == Shift.SABAH
                ? "Kalkış: " + driverLabel
                : settings.getOfficeLabel();
        String endLabel = shift == Shift.SABAH
                ? settings.getOfficeLabel()
                : "Varış: " + driverLabel;

        // Her durağa kadar biriken süre ve belirsizlik.
        int stopCount = ordered.size() + 2;
        double[] elapsed = new double[stopCount];
        double[] spread = new double[stopCount];

        double totalKm = 0;
        for (int i = 0; i < legKm.size(); i++) {
            totalKm += legKm.get(i);

            double boarding = i < ordered.size() ? settings.getBoardingMinutes() : 0;
            elapsed[i + 1] = elapsed[i] + legMinutes.get(i) + boarding;
            spread[i + 1] = spread[i] + legMinutes.get(i) * legVariation.get(i);
        }

        double totalMinutes = elapsed[stopCount - 1];

        // Sabah: 08:00'den geriye. Akşam: 17:30'dan ileriye.
        LocalTime start = shift == Shift.SABAH
                ? settings.arrivalAt().minusMinutes(Math.round(totalMinutes))
                : settings.departureAt();

        List<RouteStopDto> stops = new ArrayList<>(stopCount);

        stops.add(new RouteStopDto(
                vehicle.getId(), 0, null, startLabel,
                points.get(0)[0], points.get(0)[1], 0,
                start.format(HHMM), start.format(HHMM), 0));

        int maxRide = 0;

        for (int i = 0; i < ordered.size(); i++) {
            Worker worker = ordered.get(i);
            int stopIndex = i + 1;

            // Sabah bu kişi burada biniyor ve turun sonuna kadar araçta;
            // akşam ofisten beri araçta ve burada iniyor.
            int ride = (int) Math.round(shift == Shift.SABAH
                    ? totalMinutes - elapsed[stopIndex]
                    : elapsed[stopIndex]);
            maxRide = Math.max(maxRide, ride);

            stops.add(new RouteStopDto(
                    vehicle.getId(),
                    stopIndex,
                    worker.getId(),
                    worker.getName() + " " + worker.getSurname(),
                    worker.getLatitude(),
                    worker.getLongitude(),
                    round2(legKm.get(i)),
                    windowStart(start, elapsed[stopIndex], spread[stopIndex]),
                    windowEnd(start, elapsed[stopIndex], spread[stopIndex]),
                    ride));
        }

        int last = stopCount - 1;
        stops.add(new RouteStopDto(
                vehicle.getId(),
                last,
                null,
                endLabel,
                points.get(points.size() - 1)[0],
                points.get(points.size() - 1)[1],
                round2(legKm.get(legKm.size() - 1)),
                windowStart(start, elapsed[last], spread[last]),
                windowEnd(start, elapsed[last], spread[last]),
                0));

        // Akşam kuralı son *yolcunun* inişine bakar; şoförün eve varışı değil.
        String endTime = shift == Shift.SABAH
                ? settings.arrivalAt().format(HHMM)
                : start.plusMinutes(Math.round(elapsed[Math.max(1, last - 1)])).format(HHMM);

        return new RouteResult(
                List.copyOf(stops), geometry, totalKm, totalMinutes,
                maxRide, start.format(HHMM), endTime);
    }

    private String windowStart(LocalTime start, double elapsed, double spread) {
        return start.plusMinutes(Math.round(elapsed - spread)).format(HHMM);
    }

    private String windowEnd(LocalTime start, double elapsed, double spread) {
        return start.plusMinutes(Math.round(elapsed + spread)).format(HHMM);
    }

    // --------------------------------------------------------------- düğümler

    /** Kalkış her zaman 0 numaralı düğüm. */
    private static final int START_NODE = 0;

    /**
     * Rota düğümlerinin koordinatları: 0 = kalkış, 1..N = işçiler (verilen liste
     * sırasıyla), N+1 = varış.
     *
     * <p>
     * Uçlar sefere göre yer değiştirir — sabah şoför evinden ofise, akşam
     * ofisten şoför evine. Arada kalan işçi düğümleri aynı.
     *
     * <p>
     * Sıralama algoritmaları işçi nesneleriyle değil bu dizideki indekslerle
     * çalışıyor; {@code /table} matrisi de aynı indekslerle geldiği için "gerçek
     * yol" ile "kuş uçuşu" maliyetleri tek satır değiştirilerek takas
     * edilebiliyor.
     */
    private double[][] nodes(Shift shift, Driver driver, List<Worker> workers) {
        double[][] coordinates = new double[workers.size() + 2][];

        double[] home = driver != null
                ? new double[] { driver.getLatitude(), driver.getLongitude() }
                : new double[] { settings.getOfficeLat(), settings.getOfficeLon() };
        double[] office = new double[] { settings.getOfficeLat(), settings.getOfficeLon() };

        coordinates[START_NODE] = shift == Shift.SABAH ? home : office;
        coordinates[coordinates.length - 1] = shift == Shift.SABAH ? office : home;

        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            coordinates[i + 1] = new double[] { worker.getLatitude(), worker.getLongitude() };
        }

        return coordinates;
    }

    private static int endNode(double[][] nodes) {
        return nodes.length - 1;
    }

    /** 1..count arası işçi düğümleri. */
    private static List<Integer> workerNodes(int count) {
        return IntStream.rangeClosed(1, count).boxed().toList();
    }

    private OsrmClient.Matrix roadMatrix(double[][] nodes) {
        return osrmClient.matrix(List.of(nodes)).orElse(null);
    }

    // --------------------------------------------------------------- maliyet

    /**
     * İki düğüm arası dakika. Kaynağı ya OSRM matrisi ya kuş uçuşu tahmindir.
     * Arayüz {@link OrOpt} ile ortak: yerel arama da aynı maliyeti okuyor.
     */
    private interface LegCost extends OrOpt.LegCost {
    }

    /**
     * Bütün düğüm çiftlerinin dakikasını bir kez hesaplayıp tabloya alır.
     *
     * <p>
     * Yerel arama bu maliyeti yüz binlerce kez soruyor; her seferinde geohash
     * kodlayıp trafik tablosuna bakmak aramanın kendisinden pahalıya geliyordu.
     * En fazla 17 düğüm olduğu için tablo 289 hücre: bir kez doldur, sonrası
     * dizi okuması.
     */
    private LegCost costFunction(double[][] nodes, OsrmClient.Matrix matrix, String timeSlot) {
        int size = nodes.length;
        double[][] minutes = new double[size][size];

        double[][] roadMeters = matrix == null ? null : matrix.meters();
        double[][] roadSeconds = matrix == null ? null : matrix.seconds();
        boolean congestionKnown = trafficSpeedService.hasFreeFlowData();

        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                if (from == to) {
                    continue;
                }
                double[] start = nodes[from];
                double[] end = nodes[to];

                // Öncelik OSRM'in kendi süresi × bölgesel tıkanıklık. Sıralamanın
                // ölçtüğü şey ile arayüzde yazan süre böylece aynı modelden
                // geliyor; ikisi ayrı kaldığında arama, gerçekte ihlal olan bir
                // dağılımı kurallara uygun sanıyordu.
                double sampled = roadSeconds == null ? Double.NaN : roadSeconds[from][to];
                if (congestionKnown && Double.isFinite(sampled) && sampled > 0) {
                    minutes[from][to] = sampled / 60.0 * trafficSpeedService.legCongestionFactor(
                            start[0], start[1], end[0], end[1], timeSlot);
                    continue;
                }

                minutes[from][to] = legMinutes(start[0], start[1], end[0], end[1],
                        legKm(nodes, roadMeters, from, to), timeSlot);
            }
        }

        return (from, to) -> minutes[from][to];
    }

    /** Bacak mesafesi: matris varsa gerçek yol, yoksa kuş uçuşu × karayolu çarpanı. */
    private double legKm(double[][] nodes, double[][] roadMeters, int from, int to) {
        if (roadMeters != null) {
            double meters = roadMeters[from][to];
            // OSRM ulaşamadığı çiftlerde null döner, istemci NaN'a çevirir.
            if (Double.isFinite(meters)) {
                return meters / 1000.0;
            }
        }

        double[] start = nodes[from];
        double[] end = nodes[to];
        return straightRoadKm(start[0], start[1], end[0], end[1]);
    }

    private double straightRoadKm(double fromLat, double fromLon, double toLat, double toLon) {
        return GeoUtils.haversineKm(fromLat, fromLon, toLat, toLon) * settings.getRoadFactor();
    }

    /** Verilen yol mesafesini o koridorun sabah zirvesi ortalama hızına böler. */
    private double legMinutes(double fromLat, double fromLon, double toLat, double toLon, double km, String timeSlot) {
        double speed = trafficSpeedService.legSpeedKmh(
                fromLat, fromLon, toLat, toLon, timeSlot);
        return km / speed * 60;
    }

    // -------------------------------------------------------------- sıralama

    /** En-yakın-komşu + yerel arama ile kurulan turun süresi. */
    private double bestTourMinutes(List<Integer> workerNodes, int endNode, LegCost cost) {
        List<Integer> order = nearestNeighbour(START_NODE, workerNodes, cost);
        improve(order, endNode, cost);
        return tourMinutes(order, START_NODE, endNode, cost);
    }

    /**
     * Turu iki hamle tipiyle iyileştirir; ikisi de kazanç bulamayınca durur.
     *
     * <p>
     * <b>Neden iki tip:</b> 2-opt bir parçayı ters çevirir. Kuş uçuşu maliyet
     * simetrik olduğu için orada bedavaydı, ama gerçek yol matrisi simetrik
     * değil — ters çevirme parçanın içindeki her bacağı da ters yöne çeviriyor
     * ve o yön çok daha pahalı olabiliyor (ölçülen bir örnekte Şahin→Cansu
     * 10.76 km, Cansu→Şahin 22.47 km). Bu yüzden 2-opt tek başına asimetrik
     * matriste yerel optimumda kilitleniyordu: 6 duraklı bir serviste 50.73 km
     * bulup duruyordu, gerçek optimum 44.64 km'ydi.
     *
     * <p>
     * Boşluğu {@link OrOpt} dolduruyor: parçayı yönünü bozmadan taşıdığı için
     * asimetrik maliyetle doğru çalışıyor ve aynı serviste optimali buluyor.
     */
    private void improve(List<Integer> order, int endNode, LegCost cost) {
        if (order.size() < 3) {
            return;
        }

        for (int round = 0; round < IMPROVE_MAX_ROUNDS; round++) {
            boolean improved = twoOpt(order, endNode, cost);
            improved |= orOpt(order, endNode, cost);

            if (!improved) {
                return;
            }
        }
    }

    /**
     * Or-opt: 1-3 duraklık bir parçayı yönünü koruyarak turun başka bir yerine
     * taşır. Her turda mümkün hamlelerin en iyisi uygulanır.
     */
    private boolean orOpt(List<Integer> order, int endNode, LegCost cost) {
        return OrOpt.improve(order, START_NODE, endNode, cost, LOCAL_SEARCH_MAX_PASSES);
    }

    /** Kalkış → sıralı işçiler → ofis turunun toplam dakikası (biniş dahil). */
    private double tourMinutes(List<Integer> order, int startNode, int endNode, LegCost cost) {
        double minutes = 0;
        int current = startNode;

        for (int node : order) {
            minutes += cost.minutes(current, node) + settings.getBoardingMinutes();
            current = node;
        }

        return minutes + cost.minutes(current, endNode);
    }

    private List<Integer> nearestNeighbour(int startNode, List<Integer> workerNodes, LegCost cost) {
        List<Integer> remaining = new ArrayList<>(workerNodes);
        List<Integer> ordered = new ArrayList<>(remaining.size());
        int current = startNode;

        while (!remaining.isEmpty()) {
            int bestIndex = 0;
            double bestCost = Double.MAX_VALUE;

            for (int i = 0; i < remaining.size(); i++) {
                double candidate = cost.minutes(current, remaining.get(i));
                if (candidate < bestCost) {
                    bestCost = candidate;
                    bestIndex = i;
                }
            }

            current = remaining.remove(bestIndex);
            ordered.add(current);
        }

        return ordered;
    }

    /**
     * Uçları sabit 2-opt: [kalkış] -> işçiler -> [ofis] dizisinde kesişmeleri açar.
     *
     * <p>
     * <b>Neden turun tamamı yeniden hesaplanıyor:</b> kuş uçuşu maliyet
     * simetrikti (A→B = B→A), bu yüzden ters çevrilen parçanın iç maliyeti
     * değişmiyor ve yalnızca iki sınır bacağına bakmak yetiyordu. Gerçek yol
     * matrisi ise <b>simetrik değil</b> — tek yönler ve köprü çıkışları yüzünden
     * ters yön farklı uzunlukta. O kısayol artık yanlış sonuç verirdi. Bir
     * serviste en fazla 15 durak olduğu için turun tamamını hesaplamak yine de
     * ucuz: en kötü ihtimalle birkaç yüz bin dizi okuması.
     */
    private boolean twoOpt(List<Integer> order, int endNode, LegCost cost) {
        if (order.size() < 3) {
            return false;
        }

        boolean anyImprovement = false;
        boolean improved = true;
        int pass = 0;

        while (improved && pass++ < LOCAL_SEARCH_MAX_PASSES) {
            improved = false;
            double current = tourMinutes(order, START_NODE, endNode, cost);

            for (int i = 0; i < order.size() - 1; i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    Collections.reverse(order.subList(i, j + 1));
                    double candidate = tourMinutes(order, START_NODE, endNode, cost);

                    if (candidate < current - IMPROVEMENT_EPSILON) {
                        current = candidate;
                        improved = true;
                        anyImprovement = true;
                    } else {
                        Collections.reverse(order.subList(i, j + 1));
                    }
                }
            }
        }

        return anyImprovement;
    }

    // -------------------------------------------------------------- önbellek

    /**
     * Girdinin tamamını kapsayan anahtar: servis, şoförün konumu ve işçilerin
     * id + koordinatları. Sorgu sırası değişse de anahtar değişmesin diye
     * işçi parçaları sıralanıyor.
     */
    private String cacheKey(ServiceVehicle vehicle, Driver driver, List<Worker> workers) {
        StringBuilder key = new StringBuilder()
                .append(vehicle.getId())
                .append('|');

        if (driver != null) {
            key.append(driver.getLatitude()).append(',').append(driver.getLongitude());
        }
        key.append('|');

        workers.stream()
                .map(worker -> worker.getId() + ":" + worker.getLatitude() + ":" + worker.getLongitude())
                .sorted()
                .forEach(part -> key.append(part).append(';'));

        return key.toString();
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
