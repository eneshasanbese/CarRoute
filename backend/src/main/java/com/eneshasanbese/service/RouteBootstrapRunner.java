package com.eneshasanbese.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Uygulama açılışında servisi olmayan işçileri dağıtır. Seed verisinde bütün
 * işçilerin {@code service_vehicle_id} alanı NULL geldiği için ilk açılışta 100
 * kişiyi 10 servise bu adım yerleştirir; sonraki açılışlarda atanmamış kimse
 * kalmadığı için hiçbir şey yapmaz.
 *
 * <p>
 * Trafik verisi rota maliyetinde kullanıldığından {@link TrafficDataSeeder}
 * sonrasında çalışması gerekir — sıralama {@code @Order} ile veriliyor.
 */
@Component
@Order(2)
public class RouteBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RouteBootstrapRunner.class);

    private final AssignmentService assignmentService;

    public RouteBootstrapRunner(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Override
    public void run(String... args) {
        int assigned = assignmentService.assignUnassigned();
        if (assigned == 0) {
            log.info("Bütün işçilerin servisi belli, dağıtım atlandı.");
        } else {
            log.info("{} işçi servislere dağıtıldı.", assigned);
        }
    }
}
