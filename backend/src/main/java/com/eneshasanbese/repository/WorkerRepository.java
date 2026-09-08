package com.eneshasanbese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, Long> {

    List<Worker> findByServiceVehicleIsNull();

    List<Worker> findByServiceVehicle(ServiceVehicle serviceVehicle);

    List<Worker> findByServiceVehicleId(Long serviceVehicleId);

    long countByServiceVehicleId(Long serviceVehicleId);
}
