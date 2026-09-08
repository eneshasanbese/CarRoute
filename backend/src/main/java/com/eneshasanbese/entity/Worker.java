package com.eneshasanbese.entity;

import com.eneshasanbese.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "worker")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String surname;
    private String phone;

    private String address;
    private double latitude;
    private double longitude;

    private boolean hasCar;
    private boolean hasChild;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @ManyToOne
    @JoinColumn(name = "service_vehicle_id")
    private ServiceVehicle serviceVehicle;

}