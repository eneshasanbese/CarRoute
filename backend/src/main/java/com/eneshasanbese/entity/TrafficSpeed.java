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

}