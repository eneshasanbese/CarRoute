package com.eneshasanbese.dto;

/**
 * Personel ekleme/güncelleme gövdesi. {@code lat}/{@code lon} opsiyoneldir:
 * kullanıcı adres autocomplete listesinden seçim yaptıysa dolu gelir, aksi halde
 * koordinat adresteki ilçeye göre tahmin edilir.
 */
public record EmployeeRequest(
        String adSoyad,
        String adres,
        String cinsiyet,
        Integer yas,
        Boolean arabaliMi,
        Boolean cocukVarMi,
        String ilce,
        Double lat,
        Double lon,
        String telefon) {
}
