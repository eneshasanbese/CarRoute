package com.eneshasanbese.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpeedStatsTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    @DisplayName("Ortalama araç sayısıyla ağırlıklandırılır")
    void agirlikliOrtalama() {
        SpeedStats stats = new SpeedStats();
        stats.add("2025-01-01", 100, 1);
        stats.add("2025-01-01", 50, 3);

        // (100*1 + 50*3) / 4 = 62.5 — düz ortalama 75 olurdu.
        assertEquals(62.5, stats.average(), TOLERANCE);
    }

    @Test
    @DisplayName("Her gün aynıysa oynaklık sıfır")
    void sabitHizOynakligiSifir() {
        SpeedStats stats = new SpeedStats();
        for (int day = 1; day <= 10; day++) {
            stats.add("2025-01-" + String.format("%02d", day), 60, 5);
        }

        assertEquals(0, stats.variation(), TOLERANCE);
    }

    @Test
    @DisplayName("Günden güne oynayan hız pozitif varyasyon üretir")
    void oynakHizVaryasyonUretir() {
        SpeedStats stats = new SpeedStats();
        double[] daily = { 40, 50, 60, 50, 40, 55, 45 };
        for (int i = 0; i < daily.length; i++) {
            stats.add("2025-01-0" + (i + 1), daily[i], 1);
        }

        double variation = stats.variation();
        assertTrue(variation > 0, "oynaklık pozitif olmalı");
        // Ortalama 48.57, standart sapma ~6.7 -> ~%14
        assertEquals(0.138, variation, 0.01);
    }

    @Test
    @DisplayName("Yetersiz gün varsa oynaklık bilinmiyor sayılır")
    void azGunVarsaSifir() {
        SpeedStats stats = new SpeedStats();
        stats.add("2025-01-01", 30, 1);
        stats.add("2025-01-02", 90, 1);

        // 2 gün, eşik 5. Tek bir sapmadan oynaklık üretmek yanıltıcı olurdu;
        // çağıran taraf varsayılana düşsün diye 0 dönüyor.
        assertEquals(0, stats.variation(), TOLERANCE);
    }

    @Test
    @DisplayName("Hiç ölçüm yoksa çökmez")
    void bosIstatistik() {
        SpeedStats stats = new SpeedStats();
        assertEquals(0, stats.average(), TOLERANCE);
        assertEquals(0, stats.variation(), TOLERANCE);
    }
}
