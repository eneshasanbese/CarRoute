package com.eneshasanbese.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.dto.DriverDto;
import com.eneshasanbese.dto.DriverRequest;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.repository.DriverRepository;
import com.eneshasanbese.repository.ServiceVehicleRepository;
import com.eneshasanbese.util.AddressUtils;

/**
 * Şoför ekleme.
 *
 * <p>
 * <b>Şoför tek başına eklenmiyor.</b> Sistemde her servisin başlangıç noktası
 * kendi şoförünün ev adresi; şoförsüz bir servis ya da servissiz bir şoför
 * anlamsız. Bu yüzden tek istekte hem {@link ServiceVehicle} hem {@link Driver}
 * oluşuyor ve birbirine bağlanıyor.
 *
 * <p>
 * <b>Yeni servis boş kalmıyor.</b> Eklendikten sonra dağıtım dengeleniyor:
 * asgari doluluğa kadar en uygun yolcular taşınıyor, ardından yerel arama
 * çalışıyor. Herkesi baştan dağıtmak yerine bu tercih edildi — çoğu kişinin
 * servisi değişmiyor, yalnızca yeni servise geçmesi mantıklı olanlar taşınıyor.
 * Gerçek bir kurumda personelin servisi durduk yere değişmemeli.
 */
@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final ServiceVehicleRepository serviceVehicleRepository;
    private final AssignmentService assignmentService;
    private final LocationResolver locationResolver;
    private final RouteSettings settings;

    public DriverService(
            DriverRepository driverRepository,
            ServiceVehicleRepository serviceVehicleRepository,
            AssignmentService assignmentService,
            LocationResolver locationResolver,
            RouteSettings settings) {
        this.driverRepository = driverRepository;
        this.serviceVehicleRepository = serviceVehicleRepository;
        this.assignmentService = assignmentService;
        this.locationResolver = locationResolver;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public List<DriverDto> findAll() {
        return driverRepository.findAll().stream()
                .sorted(Comparator.comparing(Driver::getId))
                .map(DriverService::toDto)
                .toList();
    }

    /**
     * Yeni şoför ve servisini oluşturur, ardından dağıtımı dengeler.
     *
     * @return oluşan şoför (servis id'si dolu)
     */
    @Transactional
    public DriverDto create(DriverRequest request) {
        validate(request);

        double[] coordinates = locationResolver.resolve(
                request.lat(), request.lon(), request.ilce(), request.adres());

        ServiceVehicle vehicle = new ServiceVehicle();
        vehicle.setCapacity(settings.getDefaultCapacity());
        vehicle.setPlateNumber(trimmedOrNull(request.plaka()));
        vehicle.setModel(trimmedOrNull(request.model()));
        serviceVehicleRepository.save(vehicle);

        String[] parts = AddressUtils.splitFullName(request.adSoyad());
        Driver driver = new Driver();
        driver.setName(parts[0]);
        driver.setSurname(parts[1]);
        driver.setPhone(trimmedOrNull(request.telefon()));
        driver.setAddress(request.adres().trim());
        driver.setLatitude(coordinates[0]);
        driver.setLongitude(coordinates[1]);
        driver.setServiceVehicle(vehicle);
        driverRepository.save(driver);

        // Yeni servis 0 kişiyle doğuyor; asgari doluluk onarımı onu doldurur.
        assignmentService.rebalance();

        return toDto(driver);
    }

    // ------------------------------------------------------------- eşleme

    private static DriverDto toDto(Driver driver) {
        ServiceVehicle vehicle = driver.getServiceVehicle();

        return new DriverDto(
                driver.getId(),
                driver.getName() + " " + driver.getSurname(),
                driver.getPhone(),
                driver.getAddress(),
                AddressUtils.extractDistrict(driver.getAddress()),
                driver.getLatitude(),
                driver.getLongitude(),
                vehicle != null ? vehicle.getId() : null,
                vehicle != null ? vehicle.getPlateNumber() : null,
                vehicle != null ? vehicle.getModel() : null,
                vehicle != null ? vehicle.getCapacity() : 0);
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validate(DriverRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("İstek gövdesi boş.");
        }
        if (request.adSoyad() == null || request.adSoyad().trim().length() < 3) {
            throw new IllegalArgumentException("Ad soyad en az 3 karakter olmalı.");
        }
        if (request.adres() == null || request.adres().trim().length() < 5) {
            throw new IllegalArgumentException("Adres en az 5 karakter olmalı.");
        }
    }
}
