package com.eneshasanbese.config;

import java.time.LocalTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * Rota hesabının ayarları. Varsayılanlar seed dosyasındaki senaryoya göre
 * belirlendi; hepsi application.properties üzerinden değiştirilebilir.
 */
@Component
@Getter
public class RouteSettings {

    /** Ofis: Meclis Mah. Sancaktepe/İstanbul — herkesin varış noktası. */
    @Value("${carroute.office.lat:40.995800}")
    private double officeLat;

    @Value("${carroute.office.lon:29.206900}")
    private double officeLon;

    @Value("${carroute.office.label:Ofis (Sancaktepe)}")
    private String officeLabel;

    /** Ofiste olunması gereken saat; kalkış saati bundan geriye sayılır. */
    @Value("${carroute.office.arrival:08:00}")
    private String arrivalTime;

    /** Servis başına en az kişi — altına düşerse yalnızca uyarılır. */
    @Value("${carroute.capacity.min:5}")
    private int minCapacity;

    /**
     * Kuş uçuşu mesafeyi karayolu mesafesine yaklaştıran çarpan. İstanbul'un
     * yol ağı için ~1.35 makul bir kabul.
     */
    @Value("${carroute.route.road-factor:1.35}")
    private double roadFactor;

    /** Her personel durağında kapıda geçen süre (dakika). */
    @Value("${carroute.route.boarding-minutes:1.0}")
    private double boardingMinutes;

    /** Trafik verisi bulunamazsa kullanılacak ortalama hız (km/sa). */
    @Value("${carroute.route.fallback-speed:30.0}")
    private double fallbackSpeedKmh;

    /**
     * Yoğunluk yüzdesi hesabında referans serbest akış hızı (km/sa):
     * yogunluk = 100 * (1 - olculenHiz / referans).
     */
    @Value("${carroute.traffic.free-flow-speed:80.0}")
    private double freeFlowSpeedKmh;

    public LocalTime arrivalAt() {
        return LocalTime.parse(arrivalTime);
    }
}
