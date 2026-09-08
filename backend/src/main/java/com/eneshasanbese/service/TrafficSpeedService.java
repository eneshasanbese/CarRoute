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

    /** İki uç noktanın hücrelerinden bacak hızını türetir. */
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
        Map<String, double[]> sum5 = new HashMap<>();
        Map<String, double[]> sum4 = new HashMap<>();
        double total = 0;

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
        }

        return new SlotIndex(
                byCell,
                average(sum5),
                average(sum4),
                byCell.isEmpty() ? 0 : total / byCell.size());
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
            double cityAverage) {
    }
}
