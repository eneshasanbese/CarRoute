package com.eneshasanbese.dto;

/**
 * Bir servisin tek bir sefer için özeti.
 *
 * @param sefer             "sabah" veya "aksam"
 * @param kalkisSaati       sabah: 08:00'den geriye sayılan kalkış; akşam: sabit
 *                          ofis kalkışı (17:30)
 * @param varisSaati        sabah: ofise varış (08:00); akşam: son yolcunun indiği
 *                          tahmini saat
 * @param enUzunYolculukDk  en uzun süre araçta kalan yolcunun süresi — kuralın
 *                          bağladığı sayı. Şoför sayılmaz.
 * @param azamiYolculukDk   izin verilen üst sınır (varsayılan 90)
 * @param kuralIhlali       en uzun yolculuk sınırı aşıyor mu
 */
public record ServiceDto(
        Long id,
        String sefer,
        int kisiSayisi,
        int minKapasite,
        int maxKapasite,
        double toplamKm,
        int tahminiSureDk,
        String kalkisSaati,
        String varisSaati,
        int enUzunYolculukDk,
        int azamiYolculukDk,
        boolean kuralIhlali) {
}
