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

    /**
     * Ofis: Boutique Daça Alışveriş Merkezi, Soner Sokağı, Meclis Mah.
     * Sancaktepe/İstanbul — herkesin varış noktası.
     *
     * <p>
     * Değer binanın OpenStreetMap'teki merkezi (Nominatim). OSRM bu noktayı 35 m
     * ötedeki Soner Sokağı'na bağlıyor; servisler ana caddeye değil binanın
     * yanındaki sokağa varıyor. Seed dosyasındaki 40.9958 / 29.2069 çifti
     * adresle uyuşmuyordu (Eyüp Sultan Mahallesi'ne düşüyordu), ardından
     * kullanılan 41.010412 / 29.204878 ise yalnızca mahalle merkeziydi.
     */
    @Value("${carroute.office.lat:41.008493}")
    private double officeLat;

    @Value("${carroute.office.lon:29.197506}")
    private double officeLon;

    @Value("${carroute.office.label:Ofis (Sancaktepe)}")
    private String officeLabel;

    /** Ofiste olunması gereken saat; sabah kalkışı bundan geriye sayılır. */
    @Value("${carroute.office.arrival:08:00}")
    private String arrivalTime;

    /** Akşam ofisten kalkış saati; varış saatleri bundan ileriye sayılır. */
    @Value("${carroute.office.departure:17:30}")
    private String departureTime;

    /**
     * Bir yolcunun araçta geçirebileceği azami süre (dakika).
     *
     * <p>
     * Sabah bunu zorlayan kişi <b>ilk binen</b>, akşam <b>en son inen</b>.
     * Şoför kapsam dışı: bütün turu yapan o, ama bu onun işi.
     */
    @Value("${carroute.rule.max-ride-minutes:90}")
    private int maxRideMinutes;

    /**
     * Kuralı aşan bir dakika, atamada kaç dakikalık yol süresine bedel sayılır.
     *
     * <p>
     * Verimlilik ile kural arasındaki dengeyi bu belirliyor ve ikisi gerçekten
     * çatışıyor: kimseyi 90 dakikanın üstünde bırakmamak için servislerin daha
     * uzun yol gitmesi gerekiyor. 0 verilirse atama kuralı hiç görmez ve yalnızca
     * toplam süreyi küçültür; büyüttükçe ihlal azalır, kilometre artar.
     */
    @Value("${carroute.rule.penalty-weight:4.0}")
    private double rulePenaltyWeight;

    /**
     * Hız oynaklığı bilinmeyen bölgeler için varsayılan varyasyon katsayısı.
     * Ocak 2025 verisinde ölçülen medyan %6.2'ye yakın tutuldu.
     */
    @Value("${carroute.route.default-variation:0.07}")
    private double defaultVariation;

    /**
     * Yeni eklenen servis aracının kapasitesi. Mevcut araçların kapasitesi
     * kendi kaydında tutuluyor; bu yalnızca varsayılan.
     */
    @Value("${carroute.capacity.default:15}")
    private int defaultCapacity;

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

    public LocalTime arrivalAt() {
        return LocalTime.parse(arrivalTime);
    }

    public LocalTime departureAt() {
        return LocalTime.parse(departureTime);
    }
}
