package com.eneshasanbese.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "traffic_speed")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TrafficSpeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String geohash;
    private String timeSlot;
    private double avgSpeed;

    /**
     * Bu hücrenin hızının günden güne oynaklığı: günlük ortalamaların standart
     * sapmasının ortalamaya oranı (varyasyon katsayısı).
     *
     * <p>
     * Varış saatini tek bir dakika olarak vermek, sahip olmadığımız bir
     * kesinliği iddia etmek olurdu. Bu alan, arayüzde gösterilen varış
     * aralığının genişliğini uydurmak yerine veriden türetmeyi sağlıyor.
     * Ocak 2025 verisinde medyan %6.2, %75'lik dilim %9.3.
     *
     * <p>
     * Sarmalayıcı tip bilerek: alan sonradan eklendi ve mevcut satırlarda NULL
     * kalıyor. İlkel {@code double} olsaydı Hibernate kolonu NOT NULL yapmaya
     * çalışıp açılışta patlardı. NULL burada "bilinmiyor" demek ve çağıran taraf
     * varsayılan oynaklığa düşüyor.
     */
    private Double speedVariation;

}