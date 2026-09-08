package com.eneshasanbese.dto;

/**
 * Sıralı durak. {@code employeeId} null ise durak bir personel değil, rotanın
 * başlangıcı (şoförün evi) veya bitişidir (ofis).
 */
public record RouteStopDto(
        Long servisId,
        int durakNo,
        Long employeeId,
        String adSoyad,
        double lat,
        double lon,
        double oncekiDuraktanKm) {
}
