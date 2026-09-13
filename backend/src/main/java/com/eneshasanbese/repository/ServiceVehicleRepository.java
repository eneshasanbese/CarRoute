package com.eneshasanbese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eneshasanbese.entity.ServiceVehicle;

@Repository
public interface ServiceVehicleRepository extends JpaRepository<ServiceVehicle, Long> {

    List<ServiceVehicle> findAllByOrderByIdAsc();
}
