package com.eneshasanbese.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "service_vehicle")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceVehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String plateNumber;
    private String model;
    private int capacity;

    /**
     * Driver.serviceVehicle ile çift yönlü ilişki. Lombok'un ürettiği
     * equals/hashCode/toString bu döngüde sonsuza kadar dolaşacağı için bu taraf
     * dışarıda bırakıldı.
     */
    @OneToOne(mappedBy = "serviceVehicle")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Driver driver;

}
