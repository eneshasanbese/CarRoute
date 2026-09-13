package com.eneshasanbese.dto;

/**
 * Şoför ekleme ve güncelleme gövdesi. Şoför tek başına eklenmiyor: her şoför
 * bir servisin sahibi olduğu için aynı istekte yeni bir servis aracı da
 * oluşuyor. Güncellemede plaka ve model mevcut araca yazılır. Rotanın
 * başlangıç noktası şoförün ev adresi.
 *
 * <p>
 * {@code plaka} zorunlu ve benzersiz: arayüzde servisin adı. {@code telefon}
 * boş gelirse kayıtlı telefon korunur.
 *
 * <p>
 * {@code lat}/{@code lon} opsiyoneldir — personel eklemedeki gibi, autocomplete
 * listesinden seçim yapıldıysa dolu gelir.
 */
public record DriverRequest(
        String adSoyad,
        String telefon,
        String adres,
        String ilce,
        Double lat,
        Double lon,
        String plaka,
        String model) {
}
