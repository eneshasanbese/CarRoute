package com.eneshasanbese.service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eneshasanbese.dto.EmployeeDto;
import com.eneshasanbese.dto.MutationResultDto;
import com.eneshasanbese.dto.RouteStopDto;
import com.eneshasanbese.dto.ServiceDto;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.repository.ServiceVehicleRepository;
import com.eneshasanbese.repository.WorkerRepository;
import com.eneshasanbese.service.RouteService.RoutePlan;

/**
 * Servis listesi ve güzergâh sorguları.
 *
 * <p>
 * Durak sırası veritabanında tutulmaz; her istekte yeniden hesaplanır. Bir
 * serviste en fazla 15 kişi olduğu için bu hesap milisaniyeler sürer ve
 * atama değiştiği anda sonucun bayatlama ihtimalini ortadan kaldırır.
 */
@Service
public class ServiceCatalogService {

    private final ServiceVehicleRepository serviceVehicleRepository;
    private final WorkerRepository workerRepository;
    private final AssignmentService assignmentService;
    private final RouteService routeService;

    public ServiceCatalogService(
            ServiceVehicleRepository serviceVehicleRepository,
            WorkerRepository workerRepository,
            AssignmentService assignmentService,
            RouteService routeService) {
        this.serviceVehicleRepository = serviceVehicleRepository;
        this.workerRepository = workerRepository;
        this.assignmentService = assignmentService;
        this.routeService = routeService;
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> listServices() {
        Map<Long, Driver> drivers = assignmentService.driversByVehicleId();

        return serviceVehicleRepository.findAllByOrderByIdAsc().stream()
                .map(vehicle -> {
                    RoutePlan plan = planFor(vehicle, drivers.get(vehicle.getId()));
                    return routeService.toService(vehicle, plan);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceDto serviceOf(Long vehicleId) {
        ServiceVehicle vehicle = requireVehicle(vehicleId);
        Driver driver = assignmentService.driversByVehicleId().get(vehicleId);
        return routeService.toService(vehicle, planFor(vehicle, driver));
    }

    @Transactional(readOnly = true)
    public List<RouteStopDto> routeOf(Long vehicleId) {
        ServiceVehicle vehicle = requireVehicle(vehicleId);
        Driver driver = assignmentService.driversByVehicleId().get(vehicleId);
        return routeService.toStops(vehicle, driver, planFor(vehicle, driver));
    }

    /** Ekleme/güncelleme/silme yanıtı: etkilenen servisin güncel hali + rotası. */
    @Transactional(readOnly = true)
    public MutationResultDto mutationResult(EmployeeDto employee, Long vehicleId) {
        ServiceVehicle vehicle = requireVehicle(vehicleId);
        Driver driver = assignmentService.driversByVehicleId().get(vehicleId);
        RoutePlan plan = planFor(vehicle, driver);

        return new MutationResultDto(
                employee,
                routeService.toService(vehicle, plan),
                routeService.toStops(vehicle, driver, plan));
    }

    private RoutePlan planFor(ServiceVehicle vehicle, Driver driver) {
        List<Worker> workers = workerRepository.findByServiceVehicleId(vehicle.getId());
        return routeService.plan(driver, workers);
    }

    private ServiceVehicle requireVehicle(Long vehicleId) {
        return serviceVehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NoSuchElementException("Servis bulunamadı: " + vehicleId));
    }
}
