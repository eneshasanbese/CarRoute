package com.eneshasanbese.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Trafik veri setinin tanımı: hangi dosyadan geldiği ve hangi saatin hangi zaman
 * dilimine düştüğü.
 *
 * <p>
 * Satırları dilimlere ayıran seed ile dilimin saat aralığını arayüze yazan
 * trafik özeti bu tanımı ortak okuyor. Önceden saatler iki ayrı yerde yazılıydı
 * ve arayüz "07:00 – 09:00" derken veri 06:00–08:00'den geliyordu.
 */
final class TrafficDataset {

    static final String CSV_RESOURCE = "traffic_density_202501.csv";

    /** Arayüzde gösterilen kaynak — statik verinin canlı sanılmaması için. */
    static final String SOURCE_LABEL = "İBB trafik yoğunluk verisi, Ocak 2025";

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /** Başlangıç dahil, bitiş hariç. */
    private record Window(String slot, LocalTime start, LocalTime end) {

        boolean contains(LocalTime time) {
            return !time.isBefore(start) && time.isBefore(end);
        }
    }

    private static final List<Window> WINDOWS = List.of(
            new Window(TrafficSpeedService.SLOT_FREE_FLOW, LocalTime.of(1, 0), LocalTime.of(5, 0)),
            new Window(TrafficSpeedService.SLOT_MORNING, LocalTime.of(6, 0), LocalTime.of(8, 0)),
            new Window(TrafficSpeedService.SLOT_EVENING, LocalTime.of(17, 30), LocalTime.of(19, 0)));

    private TrafficDataset() {
    }

    /** Ölçümün düştüğü dilim; hiçbir dilime düşmüyorsa null. */
    static String slotOf(LocalTime time) {
        for (Window window : WINDOWS) {
            if (window.contains(time)) {
                return window.slot();
            }
        }
        return null;
    }

    /** Dilimin saat aralığı, ör. "06:00–08:00". */
    static String windowLabel(String slot) {
        return WINDOWS.stream()
                .filter(window -> window.slot().equals(slot))
                .findFirst()
                .map(window -> window.start().format(HHMM) + "–" + window.end().format(HHMM))
                .orElseThrow(() -> new IllegalArgumentException("Bilinmeyen zaman dilimi: " + slot));
    }
}
