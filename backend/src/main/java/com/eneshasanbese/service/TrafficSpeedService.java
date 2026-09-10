package com.eneshasanbese.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.dto.TrafficSnapshotDto;
import com.eneshasanbese.entity.TrafficSpeed;
import com.eneshasanbese.repository.TrafficSpeedRepository;
import com.eneshasanbese.util.GeoHash;

/**
 * traffic_speed tablosundaki geohash-6 hücrelerinden bir koordinatın ortalama
 * hızını bulur.
 *
 * <p>
 * Seed dosyasının uyardığı gibi İBB veri seti ana arterleri ölçüyor, her konut
 * mahallesini değil — adreslerin bir kısmının hücresi tabloda yok. Bu yüzden
 * kademeli bir geri çekilme uygulanır: geohash-6 → geohash-5 → geohash-4 →
 * o zaman dilimi için şehir ortalaması → sabit varsayılan hız.
 *
 * <p>
 * Trafik verisi tek seferlik seed edildiği için indeks bellekte tutulur;
 * tablo yeniden yüklenirse {@link #invalidate()} çağrılmalı.
 */
@Service
public class TrafficSpeedService {

    public static final String SLOT_MORNING = "SABAH_ZIRVE";
    public static final String SLOT_EVENING = "AKSAM_ZIRVE";
    /** Gece (01:00–05:00): her hücrenin kendi serbest akış referansı. */
    public static final String SLOT_FREE_FLOW = "SERBEST_AKIS";

    private static final int CELL_PRECISION = 6;

    private final TrafficSpeedRepository repository;
    private final RouteSettings settings;
    private final Map<String, SlotIndex> indexCache = new ConcurrentHashMap<>();

    public TrafficSpeedService(TrafficSpeedRepository repository, RouteSettings settings) {
        this.repository = repository;
        this.settings = settings;
    }

    /** Arayüzdeki "sabah"/"aksam" değerini tablodaki time_slot değerine çevirir. */
    public static String toTimeSlot(String bucket) {
        if (bucket == null) {
            return SLOT_MORNING;
        }
        return switch (bucket.toLowerCase()) {
            case "sabah" -> SLOT_MORNING;
            case "aksam", "akşam" -> SLOT_EVENING;
            default -> throw new IllegalArgumentException(
                    "Geçersiz bucket: " + bucket + " (beklenen: sabah | aksam)");
        };
    }

    public double speedKmh(double lat, double lon, String timeSlot) {
        SlotIndex index = indexCache.computeIfAbsent(timeSlot, this::buildIndex);
        String cell = GeoHash.encode(lat, lon, CELL_PRECISION);

        Double exact = index.byCell().get(cell);
        if (exact != null) {
            return exact;
        }

        Double neighbour = index.byCell5().get(cell.substring(0, 5));
        if (neighbour != null) {
            return neighbour;
        }

        Double region = index.byCell4().get(cell.substring(0, 4));
        if (region != null) {
            return region;
        }

        return index.cityAverage() > 0 ? index.cityAverage() : settings.getFallbackSpeedKmh();
    }

    /**
     * Bir noktanın tıkanıklık çarpanı: o hücre, kendi serbest akışına göre kaç
     * kat yavaş.
     *
     * <p>
     * <b>Neden mutlak hız yerine oran:</b> İBB veri seti konumla anahtarlanmış,
     * yolla değil. Bir hücrenin ölçülen hızı o hücredeki <em>ana arterin</em>
     * hızıdır; aynı hücredeki ara sokağa uygulanınca "boş sokakta 100 km/sa"
     * saçmalığı çıkıyor. Oran ise taşınabilir: "bu bölge sabahları kendi
     * normalinden %25 yavaş" ifadesi hem artere hem sokağa makul biçimde
     * uygulanabilir. Çarpan, mutlak hızı OSRM'den (yol sınıfını bilen taraf)
     * gelen süreye uygulanmak üzere üretilir.
     *
     * <p>
     * 1'in altına inemez: tıkanıklık bir aracı serbest akıştan hızlı yapamaz.
     * Bu taban, sürenin fiziksel olarak imkânsız değerlere düşmesini yapısal
     * olarak engeller.
     *
     * @return çarpan (≥ 1). Serbest akış verisi yoksa 1 döner, yani süre OSRM'in
     *         boş yol tahmininde kalır.
     */
    public double congestionFactor(double lat, double lon, String timeSlot) {
        if (!hasFreeFlowData()) {
            return 1.0;
        }

        double peak = speedKmh(lat, lon, timeSlot);
        double freeFlow = speedKmh(lat, lon, SLOT_FREE_FLOW);

        if (peak <= 0 || freeFlow <= 0) {
            return 1.0;
        }

        return Math.max(1.0, freeFlow / peak);
    }

    /**
     * Bir noktanın hızının günden güne oynaklığı (varyasyon katsayısı).
     *
     * <p>
     * Varış saatini tek bir dakika olarak vermek, sahip olmadığımız bir
     * kesinliği iddia etmek olurdu. Arayüzde gösterilen varış aralığının
     * genişliği bu değerden türetiliyor — uydurma bir ± dakika değil, verinin
     * kendi değişkenliği. Ocak 2025 sabah zirvesinde medyan %6.2.
     *
     * @return 0 ile 1 arası oran; hiç ölçüm yoksa 0
     */
    public double speedVariation(double lat, double lon, String timeSlot) {
        SlotIndex index = indexCache.computeIfAbsent(timeSlot, this::buildIndex);
        String cell = GeoHash.encode(lat, lon, CELL_PRECISION);

        Double exact = index.variationByCell().get(cell);
        if (exact != null) {
            return exact;
        }

        Double neighbour = index.variationByCell5().get(cell.substring(0, 5));
        if (neighbour != null) {
            return neighbour;
        }

        Double region = index.variationByCell4().get(cell.substring(0, 4));
        if (region != null) {
            return region;
        }

        return index.cityVariation();
    }

    /**
     * Serbest akış dilimi yüklü mü? Yüklü değilse çarpan tabanlı süre modeli
     * devre dışı kalır ve çağıran taraf hız tabanlı eski hesaba döner.
     */
    public boolean hasFreeFlowData() {
        return !indexCache.computeIfAbsent(SLOT_FREE_FLOW, this::buildIndex).byCell().isEmpty();
    }

    /** İki uç noktanın hücrelerinden bacak hızını türetir. */
    /**
     * Bir bacağın tıkanıklık çarpanı — iki uç ve orta noktadan örneklenir.
     *
     * <p>
     * Rota çizildikten sonra {@code RouteService} bu çarpanı çizginin
     * <em>tamamı</em> boyunca, mesafe ağırlıklı olarak örnekler. Burada henüz
     * çizilecek bir çizgi yok: sıralama ve atama, hangi bacakların rotaya
     * gireceği daha belli değilken karar veriyor.
     *
     * <p>
     * Yine de yalnızca iki uca bakmak sistematik olarak <b>düşük</b> tahmin
     * veriyordu: yolun büyük kısmı iki ucun arasında geçiyor ve iki ev
     * genellikle geçtikleri ana arterden daha sakin hücrelerde. Orta nokta bu
     * eğilimi kırıyor; ağırlıklar (¼, ½, ¼) yolun ortasının daha uzun olduğunu
     * yansıtıyor.
     */
    public double legCongestionFactor(
            double fromLat, double fromLon, double toLat, double toLon, String timeSlot) {

        if (!hasFreeFlowData()) {
            return 1.0;
        }

        double from = congestionFactor(fromLat, fromLon, timeSlot);
        double middle = congestionFactor((fromLat + toLat) / 2, (fromLon + toLon) / 2, timeSlot);
        double to = congestionFactor(toLat, toLon, timeSlot);

        return (from + 2 * middle + to) / 4;
    }

    public double legSpeedKmh(double fromLat, double fromLon, double toLat, double toLon, String timeSlot) {
        double from = speedKmh(fromLat, fromLon, timeSlot);
        double to = speedKmh(toLat, toLon, timeSlot);
        double average = (from + to) / 2;
        return average > 1 ? average : settings.getFallbackSpeedKmh();
    }

    public TrafficSnapshotDto snapshot(String bucket) {
        String timeSlot = toTimeSlot(bucket);
        SlotIndex index = indexCache.computeIfAbsent(timeSlot, this::buildIndex);

        double measured = index.cityAverage() > 0 ? index.cityAverage() : settings.getFallbackSpeedKmh();
        double ratio = measured / settings.getFreeFlowSpeedKmh();
        int congestion = (int) Math.round(Math.max(0, Math.min(1, 1 - ratio)) * 100);

        return new TrafficSnapshotDto(
                SLOT_MORNING.equals(timeSlot) ? "sabah" : "aksam",
                congestion,
                Instant.now().toString());
    }

    public void invalidate() {
        indexCache.clear();
    }

    private SlotIndex buildIndex(String timeSlot) {
        List<TrafficSpeed> rows = repository.findByTimeSlot(timeSlot);

        Map<String, Double> byCell = new HashMap<>();
        Map<String, Double> variationByCell = new HashMap<>();
        Map<String, double[]> sum5 = new HashMap<>();
        Map<String, double[]> sum4 = new HashMap<>();
        Map<String, double[]> variation5 = new HashMap<>();
        Map<String, double[]> variation4 = new HashMap<>();
        double total = 0;
        double variationTotal = 0;
        int variationCount = 0;

        for (TrafficSpeed row : rows) {
            String cell = row.getGeohash();
            if (cell == null || cell.length() < 4) {
                continue;
            }
            double speed = row.getAvgSpeed();
            byCell.put(cell, speed);
            total += speed;

            accumulate(sum5, cell.substring(0, 5), speed);
            accumulate(sum4, cell.substring(0, 4), speed);

            // Yeterli gün bulunmayan hücrelerde oynaklık 0 ya da NULL gelir;
            // onları ortalamaya katmak şehir oynaklığını yapay olarak düşürürdü.
            Double stored = row.getSpeedVariation();
            double variation = stored == null ? 0 : stored;
            if (variation > 0) {
                variationByCell.put(cell, variation);
                variationTotal += variation;
                variationCount++;
                accumulate(variation5, cell.substring(0, 5), variation);
                accumulate(variation4, cell.substring(0, 4), variation);
            }
        }

        return new SlotIndex(
                byCell,
                average(sum5),
                average(sum4),
                byCell.isEmpty() ? 0 : total / byCell.size(),
                variationByCell,
                average(variation5),
                average(variation4),
                variationCount == 0 ? 0 : variationTotal / variationCount);
    }

    private static void accumulate(Map<String, double[]> target, String key, double value) {
        double[] cell = target.computeIfAbsent(key, k -> new double[2]);
        cell[0] += value;
        cell[1] += 1;
    }

    private static Map<String, Double> average(Map<String, double[]> sums) {
        Map<String, Double> result = new HashMap<>();
        sums.forEach((key, cell) -> result.put(key, cell[0] / cell[1]));
        return result;
    }

    private record SlotIndex(
            Map<String, Double> byCell,
            Map<String, Double> byCell5,
            Map<String, Double> byCell4,
            double cityAverage,
            Map<String, Double> variationByCell,
            Map<String, Double> variationByCell5,
            Map<String, Double> variationByCell4,
            double cityVariation) {
    }
}
