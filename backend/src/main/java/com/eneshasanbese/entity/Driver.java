package com.eneshasanbese.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "driver")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Driver {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String surname;
    private String phone;

    private String address;
    private double latitude;
    private double longitude;

    @OneToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "service_vehicle_id")
    private ServiceVehicle serviceVehicle;
}
