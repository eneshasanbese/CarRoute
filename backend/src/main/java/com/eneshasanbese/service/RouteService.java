package com.eneshasanbese.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.dto.RouteDto;
import com.eneshasanbese.dto.RouteStopDto;
import com.eneshasanbese.dto.ServiceDto;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.util.GeoUtils;

/**
 * Tek bir servisin güzergâhını kurar.
 *
 * <p>
 * Rota şoförün ev adresinden başlar, atanmış işçileri sırayla toplar ve ofiste
 * biter (sabit garaj yok — seed dosyasındaki senaryo bu). Sıralama önce
 * en-yakın-komşu ile kurulur, ardından 2-opt ile iyileştirilir; iki uç nokta
 * (şoför evi ve ofis) sabit tutulur.
 *
 * <p>
 * <b>Maliyetin kaynağı iki türlü, bilerek:</b>
 * <ul>
 * <li><i>Gerçek yol matrisi</i> — {@link #build} ve {@link #insertionCost}
 * OSRM'in {@code /table} servisinden servis başına tek istekle N×N yol
 * mesafesi alır. Sıralama ve tek kişilik atama kararı bu matris üzerinden
 * verilir; kuş uçuşu 2 km olan iki nokta yoldan 9 km ise algoritma artık bunu
 * görür.</li>
 * <li><i>Kuş uçuşu × karayolu çarpanı</i> — {@link #estimateMinutes} toplu
 * dağıtımda (yüzlerce işçi × onlarca servis × birkaç tur) binlerce kez
 * çağrıldığı için ağ isteği yapamaz; orada eski ucuz tahmin sürüyor. OSRM
 * ulaşılamadığında bütün yollar zaten buraya düşer.</li>
 * </ul>
 *
 * <p>
 * Süre her iki durumda da yol mesafesinin o bölgenin sabah zirvesi ortalama
 * hızına bölünmesiyle bulunur — mesafe OSRM'den, hız İBB verisinden. OSRM'in
 * kendi {@code duration} değeri bilerek kullanılmıyor: o boş yolun süresidir,
 * İstanbul sabahını bilmez.
 */
@Service
public class RouteService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int LOCAL_SEARCH_MAX_PASSES = 50;
    /** 2-opt ile Or-opt turlarının birbirini besleme sayısı. */
    private static final int IMPROVE_MAX_ROUNDS = 10;
    /** Or-opt'ta taşınabilecek en uzun durak dizisi. */
    private static final int OR_OPT_MAX_SEGMENT = 3;
    /** Bir hamlenin kabul edilmesi için gereken en az kazanç (dakika). */
    private static final double IMPROVEMENT_EPSILON = 1e-9;
    /** Rota önbelleğinin üst sınırı; aşılınca tamamen boşaltılır. */
    private static final int ROUTE_CACHE_LIMIT = 250;

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
            double totalMinutes) {
    }

    /**
     * Bir işçiyi bir servise eklemenin maliyeti. İkisi de <b>aynı</b> yol matrisi
     * üzerinden hesaplandığı için farkları anlamlıdır.
     */
    public record InsertionCost(double withoutCandidate, double withCandidate) {

        /** Adayın servise getirdiği ek süre (dakika). */
        public double delta() {
            return withCandidate - withoutCandidate;
        }
    }

    public RouteResult build(ServiceVehicle vehicle, Driver driver, List<Worker> workers) {
        String key = cacheKey(vehicle, driver, workers);

        RouteResult cached = routeCache.get(key);
        if (cached != null) {
            return cached;
        }

        RouteResult result = compute(vehicle, driver, workers);

        if (routeCache.size() >= ROUTE_CACHE_LIMIT) {
            routeCache.clear();
        }
        routeCache.put(key, result);

        return result;
    }

    /**
     * Toplu dağıtımda kullanılan hafif maliyet: OSRM'siz, 2-opt'suz, yalnızca
     * en-yakın-komşu turunun kuş uçuşu süresi.
     *
     * <p>
     * {@link AssignmentService} bunu yüz binlerce kez çağırabilir; buraya ağ
     * isteği koymak dağıtımı dakikalar sürecek hale getirirdi.
     */
    public double estimateMinutes(Driver driver, List<Worker> workers) {
        double[][] nodes = nodes(driver, workers);
        LegCost cost = costFunction(nodes, null);

        List<Integer> order = nearestNeighbour(START_NODE, workerNodes(workers.size()), cost);
        return tourMinutes(order, START_NODE, officeNode(nodes), cost);
    }

    /**
     * Bir işçiyi bir servise eklemenin gerçek yol maliyeti — {@code /table}'dan
     * gelen matris üzerinde, hem adaysız hem adaylı en iyi tur kurularak.
     *
     * <p>
     * Aday matrise dahil edildiği için tek istek iki senaryoya da yetiyor. Kişi
     * turun sonuna eklenmiyor, tur baştan kuruluyor: servis zaten o mahalleden
     * geçiyorsa {@link InsertionCost#delta()} küçük, sapma gerekiyorsa büyük
     * çıkar.
     */
    public InsertionCost insertionCost(Driver driver, List<Worker> current, Worker candidate) {
        List<Worker> withCandidate = new ArrayList<>(current);
        withCandidate.add(candidate);

        double[][] nodes = nodes(driver, withCandidate);
        int officeNode = officeNode(nodes);
        double[][] roadMeters = roadMatrix(nodes);
        LegCost cost = costFunction(nodes, roadMeters);

        // Aday listenin sonunda, yani son işçi düğümü. "Adaysız" senaryo için onu
        // düğüm listesinden çıkarmak yeterli.
        return new InsertionCost(
                bestTourMinutes(workerNodes(current.size()), officeNode, cost),
                bestTourMinutes(workerNodes(withCandidate.size()), officeNode, cost));
    }

    public ServiceDto toService(ServiceVehicle vehicle, RouteResult result, int kisiSayisi) {
        int durationMinutes = (int) Math.round(result.totalMinutes());

        return new ServiceDto(
                vehicle.getId(),
                kisiSayisi,
                settings.getMinCapacity(),
                vehicle.getCapacity(),
                round1(result.totalKm()),
                durationMinutes,
                departureTime(durationMinutes));
    }

    public RouteDto toRoute(RouteResult result) {
        return new RouteDto(result.stops(), result.geometry());
    }

    /** Ofiste 08:00'de olunacak şekilde geriye sayılan kalkış saati. */
    public String departureTime(int durationMinutes) {
        return settings.arrivalAt().minusMinutes(durationMinutes).format(HHMM);
    }

    // ---------------------------------------------------------------- kurgu

    private RouteResult compute(ServiceVehicle vehicle, Driver driver, List<Worker> workers) {
        double[][] nodes = nodes(driver, workers);
        int officeNode = officeNode(nodes);

        // Sıralamanın tamamı bu tek matris üzerinden yapılır.
        double[][] roadMeters = roadMatrix(nodes);
        LegCost cost = costFunction(nodes, roadMeters);

        List<Integer> order = nearestNeighbour(START_NODE, workerNodes(workers.size()), cost);
        improve(order, officeNode, cost);

        List<Worker> ordered = order.stream().map(node -> workers.get(node - 1)).toList();

        List<Integer> sequence = new ArrayList<>(order.size() + 2);
        sequence.add(START_NODE);
        sequence.addAll(order);
        sequence.add(officeNode);

        List<double[]> points = sequence.stream().map(node -> nodes[node]).toList();

        // Sıra kesinleşti; çizgiyi ve o sıranın kesin bacaklarını almak için tek
        // bir /route isteği.
        Optional<OsrmClient.Route> osrmRoute = osrmClient.route(points);
        List<double[]> geometry = osrmRoute.map(OsrmClient.Route::geometry).orElse(null);

        List<Double> legKm = legDistancesKm(nodes, roadMeters, sequence, osrmRoute);
        List<Double> legMinutes = legDurationsMinutes(points, geometry, legKm, osrmRoute);

        return assemble(vehicle, driver, ordered, points, legKm, legMinutes, geometry);
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
            Optional<OsrmClient.Route> osrmRoute) {

        // 3. kademe: iki uç hızı. Diğerleri bunun üzerine yazar.
        List<Double> minutes = new ArrayList<>(legKm.size());
        for (int i = 0; i < legKm.size(); i++) {
            double[] from = points.get(i);
            double[] to = points.get(i + 1);
            minutes.add(legMinutes(from[0], from[1], to[0], to[1], legKm.get(i)));
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
                double factor = sampledCongestionFactor(geometry, boundaries[i], boundaries[i + 1]);
                if (freeFlowMinutes > 0 && factor > 0) {
                    minutes.set(i, freeFlowMinutes * factor);
                    continue;
                }
            }

            // 2. kademe
            double speed = sampledSpeedKmh(geometry, boundaries[i], boundaries[i + 1]);
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
    private double sampledCongestionFactor(List<double[]> geometry, int from, int to) {
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
                    TrafficSpeedService.SLOT_MORNING);

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
    private double sampledSpeedKmh(List<double[]> geometry, int from, int to) {
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
                    TrafficSpeedService.SLOT_MORNING);

            km += segment;
            hours += segment / speed;
        }

        return hours > 0 ? km / hours : 0;
    }

    private RouteResult assemble(
            ServiceVehicle vehicle,
            Driver driver,
            List<Worker> ordered,
            List<double[]> points,
            List<Double> legKm,
            List<Double> legMinutes,
            List<double[]> geometry) {

        String startLabel = driver != null
                ? "Kalkış: " + driver.getName() + " " + driver.getSurname()
                : "Kalkış noktası";

        List<RouteStopDto> stops = new ArrayList<>();
        stops.add(new RouteStopDto(
                vehicle.getId(), 0, null, startLabel, points.get(0)[0], points.get(0)[1], 0));

        double totalKm = 0;
        double totalMinutes = 0;

        for (int i = 0; i < ordered.size(); i++) {
            Worker worker = ordered.get(i);
            double km = legKm.get(i);

            totalKm += km;
            totalMinutes += legMinutes.get(i);
            totalMinutes += settings.getBoardingMinutes();

            stops.add(new RouteStopDto(
                    vehicle.getId(),
                    i + 1,
                    worker.getId(),
                    worker.getName() + " " + worker.getSurname(),
                    worker.getLatitude(),
                    worker.getLongitude(),
                    round2(km)));
        }

        double lastKm = legKm.get(legKm.size() - 1);
        totalKm += lastKm;
        totalMinutes += legMinutes.get(legMinutes.size() - 1);

        stops.add(new RouteStopDto(
                vehicle.getId(),
                ordered.size() + 1,
                null,
                settings.getOfficeLabel(),
                settings.getOfficeLat(),
                settings.getOfficeLon(),
                round2(lastKm)));

        return new RouteResult(List.copyOf(stops), geometry, totalKm, totalMinutes);
    }

    // --------------------------------------------------------------- düğümler

    /** Kalkış her zaman 0 numaralı düğüm. */
    private static final int START_NODE = 0;

    /**
     * Rota düğümlerinin koordinatları: 0 = kalkış (şoför evi), 1..N = işçiler
     * (verilen liste sırasıyla), N+1 = ofis.
     *
     * <p>
     * Sıralama algoritmaları işçi nesneleriyle değil bu dizideki indekslerle
     * çalışıyor; {@code /table} matrisi de aynı indekslerle geldiği için "gerçek
     * yol" ile "kuş uçuşu" maliyetleri tek satır değiştirilerek takas
     * edilebiliyor.
     */
    private double[][] nodes(Driver driver, List<Worker> workers) {
        double[][] coordinates = new double[workers.size() + 2][];

        coordinates[START_NODE] = driver != null
                ? new double[] { driver.getLatitude(), driver.getLongitude() }
                : new double[] { settings.getOfficeLat(), settings.getOfficeLon() };

        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            coordinates[i + 1] = new double[] { worker.getLatitude(), worker.getLongitude() };
        }

        coordinates[coordinates.length - 1] = new double[] {
                settings.getOfficeLat(), settings.getOfficeLon() };

        return coordinates;
    }

    private static int officeNode(double[][] nodes) {
        return nodes.length - 1;
    }

    /** 1..count arası işçi düğümleri. */
    private static List<Integer> workerNodes(int count) {
        return IntStream.rangeClosed(1, count).boxed().toList();
    }

    private double[][] roadMatrix(double[][] nodes) {
        return osrmClient.distanceMatrixMeters(List.of(nodes)).orElse(null);
    }

    // --------------------------------------------------------------- maliyet

    /** İki düğüm arası dakika. Kaynağı ya OSRM matrisi ya kuş uçuşu tahmindir. */
    @FunctionalInterface
    private interface LegCost {
        double minutes(int from, int to);
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
    private LegCost costFunction(double[][] nodes, double[][] roadMeters) {
        int size = nodes.length;
        double[][] minutes = new double[size][size];

        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                if (from == to) {
                    continue;
                }
                double[] start = nodes[from];
                double[] end = nodes[to];
                minutes[from][to] = legMinutes(start[0], start[1], end[0], end[1],
                        legKm(nodes, roadMeters, from, to));
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
    private double legMinutes(double fromLat, double fromLon, double toLat, double toLon, double km) {
        double speed = trafficSpeedService.legSpeedKmh(
                fromLat, fromLon, toLat, toLon, TrafficSpeedService.SLOT_MORNING);
        return km / speed * 60;
    }

    // -------------------------------------------------------------- sıralama

    /** En-yakın-komşu + yerel arama ile kurulan turun süresi. */
    private double bestTourMinutes(List<Integer> workerNodes, int officeNode, LegCost cost) {
        List<Integer> order = nearestNeighbour(START_NODE, workerNodes, cost);
        improve(order, officeNode, cost);
        return tourMinutes(order, START_NODE, officeNode, cost);
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
     * Or-opt ise 1-3 duraklık bir parçayı <b>yönünü bozmadan</b> başka bir yere
     * taşır; asimetrik maliyetle doğru çalışan hamle budur. Aynı serviste
     * optimali buluyor.
     */
    private void improve(List<Integer> order, int officeNode, LegCost cost) {
        if (order.size() < 3) {
            return;
        }

        for (int round = 0; round < IMPROVE_MAX_ROUNDS; round++) {
            boolean improved = twoOpt(order, officeNode, cost);
            improved |= orOpt(order, officeNode, cost);

            if (!improved) {
                return;
            }
        }
    }

    /**
     * Or-opt: 1-3 duraklık bir parçayı yönünü koruyarak turun başka bir yerine
     * taşır. Her turda mümkün hamlelerin en iyisi uygulanır.
     */
    private boolean orOpt(List<Integer> order, int officeNode, LegCost cost) {
        boolean anyImprovement = false;

        for (int pass = 0; pass < LOCAL_SEARCH_MAX_PASSES; pass++) {
            List<Integer> better = bestOrOptMove(order, officeNode, cost);
            if (better == null) {
                return anyImprovement;
            }

            order.clear();
            order.addAll(better);
            anyImprovement = true;
        }

        return anyImprovement;
    }

    /** En çok kazandıran tek Or-opt hamlesinin sonucu; kazanç yoksa null. */
    private List<Integer> bestOrOptMove(List<Integer> order, int officeNode, LegCost cost) {
        double best = tourMinutes(order, START_NODE, officeNode, cost);
        List<Integer> bestOrder = null;

        int maxSegment = Math.min(OR_OPT_MAX_SEGMENT, order.size() - 1);

        for (int length = 1; length <= maxSegment; length++) {
            for (int from = 0; from + length <= order.size(); from++) {
                List<Integer> rest = new ArrayList<>(order);
                List<Integer> segment = new ArrayList<>(rest.subList(from, from + length));
                rest.subList(from, from + length).clear();

                for (int to = 0; to <= rest.size(); to++) {
                    if (to == from) {
                        // Aynı yere geri koymak turu değiştirmez.
                        continue;
                    }

                    List<Integer> candidate = new ArrayList<>(rest);
                    candidate.addAll(to, segment);

                    double value = tourMinutes(candidate, START_NODE, officeNode, cost);
                    if (value < best - IMPROVEMENT_EPSILON) {
                        best = value;
                        bestOrder = candidate;
                    }
                }
            }
        }

        return bestOrder;
    }

    /** Kalkış → sıralı işçiler → ofis turunun toplam dakikası (biniş dahil). */
    private double tourMinutes(List<Integer> order, int startNode, int officeNode, LegCost cost) {
        double minutes = 0;
        int current = startNode;

        for (int node : order) {
            minutes += cost.minutes(current, node) + settings.getBoardingMinutes();
            current = node;
        }

        return minutes + cost.minutes(current, officeNode);
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
    private boolean twoOpt(List<Integer> order, int officeNode, LegCost cost) {
        if (order.size() < 3) {
            return false;
        }

        boolean anyImprovement = false;
        boolean improved = true;
        int pass = 0;

        while (improved && pass++ < LOCAL_SEARCH_MAX_PASSES) {
            improved = false;
            double current = tourMinutes(order, START_NODE, officeNode, cost);

            for (int i = 0; i < order.size() - 1; i++) {
                for (int j = i + 1; j < order.size(); j++) {
                    Collections.reverse(order.subList(i, j + 1));
                    double candidate = tourMinutes(order, START_NODE, officeNode, cost);

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
