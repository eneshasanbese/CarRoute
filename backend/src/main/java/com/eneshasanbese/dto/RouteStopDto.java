package com.eneshasanbese.dto;

/**
 * Sıralı durak. {@code employeeId} null ise durak bir personel değil, rotanın
 * başlangıcı veya bitişidir (sabah: şoför evi → ofis, akşam: ofis → şoför evi).
 *
 * @param varisSaatiErken tahmini varış penceresinin başı ("06:58")
 * @param varisSaatiGec   tahmini varış penceresinin sonu ("07:04")
 * @param yolculukDk      bu yolcunun araçta geçirdiği süre; sabah bindiği andan
 *                        ofise, akşam ofisten indiği ana kadar. Depo duraklarında
 *                        0.
 */
public record RouteStopDto(
        Long servisId,
        int durakNo,
        Long employeeId,
        String adSoyad,
        double lat,
        double lon,
        double oncekiDuraktanKm,
        String varisSaatiErken,
        String varisSaatiGec,
        int yolculukDk) {
}
