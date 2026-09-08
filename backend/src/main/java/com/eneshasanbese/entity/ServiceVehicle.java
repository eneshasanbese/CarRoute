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

    @OneToOne(mappedBy = "serviceVehicle")
    private Driver driver;

}
