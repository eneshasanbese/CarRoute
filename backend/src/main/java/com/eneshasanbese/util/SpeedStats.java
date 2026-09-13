package com.eneshasanbese.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Bir geohash hücresinin tek bir zaman dilimindeki hız istatistikleri.
 *
 * <p>
 * İki farklı şey topluyor, bilerek:
 * <ul>
 * <li><b>Ortalama hız</b> — bütün ölçümlerin araç sayısıyla ağırlıklandırılmış
 * ortalaması. Süre hesabında kullanılan değer bu.</li>
 * <li><b>Günden güne oynaklık</b> — her günün kendi ortalaması ayrı tutulup
 * aralarındaki standart sapma alınıyor. Arayüzde gösterilen varış aralığının
 * genişliği buradan geliyor; böylece aralık uydurma bir ±3 dakika değil,
 * verinin kendi değişkenliği oluyor.</li>
 * </ul>
 */
public class SpeedStats {

    /** Bir hücrenin oynaklığını hesaplamak için gereken en az gün sayısı. */
    private static final int MIN_DAYS = 5;

    private final Accumulator overall = new Accumulator();
    private final Map<String, Accumulator> byDay = new HashMap<>();

    public void add(String day, double speed, int vehicleCount) {
        overall.add(speed, vehicleCount);
        byDay.computeIfAbsent(day, key -> new Accumulator()).add(speed, vehicleCount);
    }

    /** Araç sayısıyla ağırlıklandırılmış ortalama hız (km/sa). */
    public double average() {
        return overall.average();
    }

    /**
     * Varyasyon katsayısı: günlük ortalamaların standart sapmasının ortalamaya
     * oranı.
     *
     * @return 0 ile 1 arası oran; yeterli gün yoksa 0 (yani "oynaklık bilinmiyor",
     *         çağıran taraf varsayılana düşer)
     */
    public double variation() {
        double[] dailyAverages = byDay.values().stream()
                .mapToDouble(Accumulator::average)
                .filter(value -> value > 0)
                .toArray();

        if (dailyAverages.length < MIN_DAYS) {
            return 0;
        }

        double mean = 0;
        for (double value : dailyAverages) {
            mean += value;
        }
        mean /= dailyAverages.length;

        if (mean <= 0) {
            return 0;
        }

        double variance = 0;
        for (double value : dailyAverages) {
            variance += Math.pow(value - mean, 2);
        }
        variance /= dailyAverages.length;

        return Math.sqrt(variance) / mean;
    }
}
