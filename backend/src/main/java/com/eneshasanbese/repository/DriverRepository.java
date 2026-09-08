package com.eneshasanbese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eneshasanbese.entity.Driver;

@Repository
public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByServiceVehicleId(Long serviceVehicleId);

    List<Driver> findAllByServiceVehicleIdIn(List<Long> serviceVehicleIds);
}
