package com.eneshasanbese.dto;

/** Şoför ve sürdüğü servis. */
public record DriverDto(
        Long id,
        String adSoyad,
        String telefon,
        String adres,
        String ilce,
        double lat,
        double lon,
        Long servisId,
        String plaka,
        String model,
        int kapasite) {
}
