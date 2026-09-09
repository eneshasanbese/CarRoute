package com.eneshasanbese.service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eneshasanbese.dto.EmployeeDto;
import com.eneshasanbese.dto.MutationResultDto;
import com.eneshasanbese.dto.RouteDto;
import com.eneshasanbese.dto.ServiceDto;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.repository.ServiceVehicleRepository;
import com.eneshasanbese.repository.WorkerRepository;
import com.eneshasanbese.service.RouteService.RouteResult;

/**
 * Servis listesi ve güzergâh sorguları.
 *
 * <p>
 * Durak sırası veritabanında tutulmaz; her istekte yeniden hesaplanır. Bir
 * serviste en fazla 15 kişi olduğu için bu hesap ucuzdur ve atama değiştiği anda
 * sonucun bayatlama ihtimalini ortadan kaldırır. OSRM açıkken servis başına bir
 * yol isteği yapılır.
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
                    List<Worker> workers = workersOf(vehicle);
                    RouteResult result = routeService.build(
                            vehicle, drivers.get(vehicle.getId()), workers);
                    return routeService.toService(vehicle, result, workers.size());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceDto serviceOf(Long vehicleId) {
        ServiceVehicle vehicle = requireVehicle(vehicleId);
        List<Worker> workers = workersOf(vehicle);
        RouteResult result = routeService.build(vehicle, driverOf(vehicleId), workers);
        return routeService.toService(vehicle, result, workers.size());
    }

    @Transactional(readOnly = true)
    public RouteDto routeOf(Long vehicleId) {
        ServiceVehicle vehicle = requireVehicle(vehicleId);
        RouteResult result = routeService.build(vehicle, driverOf(vehicleId), workersOf(vehicle));
        return routeService.toRoute(result);
    }

    /** Ekleme/güncelleme/silme yanıtı: etkilenen servisin güncel hali + rotası. */
    @Transactional(readOnly = true)
    public MutationResultDto mutationResult(EmployeeDto employee, Long vehicleId) {
        ServiceVehicle vehicle = requireVehicle(vehicleId);
        List<Worker> workers = workersOf(vehicle);
        RouteResult result = routeService.build(vehicle, driverOf(vehicleId), workers);

        return new MutationResultDto(
                employee,
                routeService.toService(vehicle, result, workers.size()),
                routeService.toRoute(result));
    }

    private List<Worker> workersOf(ServiceVehicle vehicle) {
        return workerRepository.findByServiceVehicleId(vehicle.getId());
    }

    private Driver driverOf(Long vehicleId) {
        return assignmentService.driversByVehicleId().get(vehicleId);
    }

    private ServiceVehicle requireVehicle(Long vehicleId) {
        return serviceVehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NoSuchElementException("Servis bulunamadı: " + vehicleId));
    }
}
