package com.eneshasanbese.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Sistemdeki bütün noktaların birbirine yol mesafesi — tek bir OSRM
 * {@code /table} isteğiyle çıkarılıp bellekte tutulur.
 *
 * <p>
 * <b>Neden var:</b> atama katmanı "bu kişiyi hangi servise koyalım" sorusunu
 * yüz binlerce kez soruyor. Her soru için OSRM'e gitmek dakikalar sürerdi, bu
 * yüzden dağıtım bugüne kadar kuş uçuşu mesafeyle çalışıyordu — Boğaz'ın iki
 * yakasındaki iki ev kuş uçuşu 2 km, yoldan 20 km. Matris bu ikilemi ortadan
 * kaldırıyor: 114 nokta için <b>bir</b> istek atılır, sonrası dizi okuması.
 *
 * <p>
 * Matris <b>simetrik değildir</b>; tek yönler ve köprü çıkışları yüzünden A→B
 * ile B→A farklı çıkar. Okuyan tarafın simetri varsayması hata olur.
 *
 * <p>
 * Bilinmeyen ya da OSRM'in ulaşamadığı çiftler {@link Double#NaN} döner;
 * çağıran taraf yalnızca o bacak için kuş uçuşu tahmine düşer.
 */
public final class RoadMatrix {

    /** Ofis her zaman 0 numaralı nokta. */
    public static final int OFFICE = 0;
    /** Tanınmayan kişi/şoför için nokta numarası. */
    public static final int UNKNOWN = -1;

    private final Map<Long, Integer> workerIndex;
    private final Map<Long, Integer> driverIndex;
    private final double[][] meters;
    private final double[][] seconds;

    public RoadMatrix(Map<Long, Integer> workerIndex, Map<Long, Integer> driverIndex,
            double[][] meters, double[][] seconds) {
        this.workerIndex = new HashMap<>(workerIndex);
        this.driverIndex = new HashMap<>(driverIndex);
        this.meters = meters;
        this.seconds = seconds;
    }

    public int worker(Long workerId) {
        return workerId == null ? UNKNOWN : workerIndex.getOrDefault(workerId, UNKNOWN);
    }

    public int driver(Long driverId) {
        return driverId == null ? UNKNOWN : driverIndex.getOrDefault(driverId, UNKNOWN);
    }

    /**
     * İki nokta arası yol mesafesi (metre). Noktalardan biri tanınmıyorsa ya da
     * OSRM o çifti bağlayamadıysa {@link Double#NaN}.
     */
    public double meters(int from, int to) {
        if (from == UNKNOWN || to == UNKNOWN) {
            return Double.NaN;
        }
        if (from == to) {
            return 0;
        }
        return meters[from][to];
    }

    /** Aynı ölçü kilometre cinsinden; bilinmiyorsa {@link Double#NaN}. */
    public double km(int from, int to) {
        return meters(from, to) / 1000.0;
    }

    /**
     * İki nokta arası <b>boş yol</b> süresi (saniye) — trafiksiz hali. Gerçek
     * süre bunun bölgesel tıkanıklık çarpanıyla ölçeklenmesiyle bulunur.
     */
    public double seconds(int from, int to) {
        if (seconds == null || from == UNKNOWN || to == UNKNOWN) {
            return Double.NaN;
        }
        if (from == to) {
            return 0;
        }
        return seconds[from][to];
    }

    public int size() {
        return meters.length;
    }

    // ------------------------------------------------------ dakika tablosu

    /**
     * Zaman dilimi -> bütün ikililerin sürüş dakikası.
     *
     * <p>
     * <b>Neden önceden hesaplanıyor:</b> iki nokta arası süre, o noktalara kimin
     * atandığından bağımsız. Atama araması aynı bacağın süresini on binlerce kez
     * soruyor; her seferinde tıkanıklık çarpanını yeniden örneklemek hem
     * aramanın kendisinden pahalıydı hem de o maliyet yüzünden örnekleme kaba
     * tutulmak zorunda kalıyordu. Tablo bir kez dolduğunda örnekleme
     * sıklaştırılabiliyor ve sorgu dizi okumasına iniyor.
     */
    private final Map<String, double[][]> minutesBySlot = new HashMap<>();

    public void putMinutes(String timeSlot, double[][] minutes) {
        minutesBySlot.put(timeSlot, minutes);
    }

    /** Verilen dilimin dakika tablosu; hesaplanmadıysa null. */
    public double[][] minutes(String timeSlot) {
        return minutesBySlot.get(timeSlot);
    }
}
