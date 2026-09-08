package com.eneshasanbese.dto;

/** Arayüzdeki {@code Service} tipinin birebir karşılığı. */
public record ServiceDto(
        Long id,
        int kisiSayisi,
        int minKapasite,
        int maxKapasite,
        double toplamKm,
        int tahminiSureDk,
        String kalkisSaati) {
}
