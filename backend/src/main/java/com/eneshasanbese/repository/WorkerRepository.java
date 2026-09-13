package com.eneshasanbese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, Long> {

    /**
     * Atama sırası sonucu etkiliyor: açgözlü kurulum işçileri sırayla
     * yerleştiriyor ve yerel arama oradan devam ediyor. {@code findAll()} satır
     * sırasını garanti etmediği için (Postgres güncellenen satırları fiziksel
     * olarak taşır) aynı veriyle yapılan iki dağıtım farklı sonuç veriyordu.
     * Aşağıdaki iki sorgu sırayı sabitleyerek dağıtımı tekrarlanabilir kılıyor.
     */
    List<Worker> findAllByOrderByIdAsc();

    List<Worker> findByServiceVehicleIsNullOrderByIdAsc();

    List<Worker> findByServiceVehicle(ServiceVehicle serviceVehicle);

    List<Worker> findByServiceVehicleId(Long serviceVehicleId);

    long countByServiceVehicleId(Long serviceVehicleId);
}
