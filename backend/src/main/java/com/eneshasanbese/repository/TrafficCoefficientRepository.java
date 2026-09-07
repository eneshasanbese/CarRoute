package com.eneshasanbese.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eneshasanbese.entity.TrafficCoefficient;

@Repository
public interface TrafficCoefficientRepository extends JpaRepository<TrafficCoefficient, Long> {

}
