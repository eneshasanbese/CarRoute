package com.eneshasanbese.dto;

/** Arayüzdeki {@code Employee} tipinin birebir karşılığı. */
public record EmployeeDto(
        Long id,
        String adSoyad,
        String cinsiyet,
        int yas,
        String adres,
        String ilce,
        double lat,
        double lon,
        boolean arabaliMi,
        boolean cocukVarMi,
        Long servisId) {
}
