package com.eneshasanbese.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.dto.DriverDeletionDto;
import com.eneshasanbese.dto.DriverDto;
import com.eneshasanbese.dto.DriverRequest;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.repository.DriverRepository;
import com.eneshasanbese.repository.ServiceVehicleRepository;
import com.eneshasanbese.repository.WorkerRepository;
import com.eneshasanbese.util.AddressUtils;
import com.eneshasanbese.util.GeoUtils;

/**
 * Şoför ekleme, güncelleme ve silme.
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
 *
 * <p>
 * <b>Şoför silinince servisi de silinir.</b> O servisin yolcuları kalan
 * servislere dağıtılır; kalan kapasite bütün personeli taşımaya yetmiyorsa
 * silme reddedilir, kimse servissiz bırakılmaz.
 *
 * <p>
 * <b>Servisin adı aracın plakası.</b> Servis id'si silme ve eklemelerle
 * boşluklu ilerlediği için (10 servis varken "Servis-12") arayüzde ad olarak
 * kullanılmıyor. Bu yüzden plaka zorunlu ve iki araçta aynı olamaz.
 */
@Service
public class DriverService {

    /** Ev adresi bu mesafeden fazla oynadıysa dağıtım yeniden dengelenir. */
    private static final double REBALANCE_THRESHOLD_KM = 0.15;

    private final DriverRepository driverRepository;
    private final ServiceVehicleRepository serviceVehicleRepository;
    private final WorkerRepository workerRepository;
    private final AssignmentService assignmentService;
    private final LocationResolver locationResolver;
    private final RouteSettings settings;

    public DriverService(
            DriverRepository driverRepository,
            ServiceVehicleRepository serviceVehicleRepository,
            WorkerRepository workerRepository,
            AssignmentService assignmentService,
            LocationResolver locationResolver,
            RouteSettings settings) {
        this.driverRepository = driverRepository;
        this.serviceVehicleRepository = serviceVehicleRepository;
        this.workerRepository = workerRepository;
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

        // Adres çözülemezse araç kaydedilmeden hata dönsün diye önce şoför dolduruluyor.
        Driver driver = new Driver();
        applyRequest(driver, request);
        requireUniquePlate(request.plaka(), null);

        ServiceVehicle vehicle = new ServiceVehicle();
        vehicle.setCapacity(settings.getDefaultCapacity());
        applyVehicle(vehicle, request);
        serviceVehicleRepository.save(vehicle);

        driver.setServiceVehicle(vehicle);
        driverRepository.save(driver);

        // Yeni servis 0 kişiyle doğuyor; asgari doluluk onarımı onu doldurur.
        assignmentService.rebalance();

        return toDto(driver);
    }

    /**
     * Şoförü ve aracını günceller.
     *
     * <p>
     * Rota şoförün evinden başlayıp orada bittiği için taşınan bir şoför, hangi
     * yolcunun hangi serviste olması gerektiğini de değiştirir. Adres eşiği
     * aşacak kadar oynadıysa dağıtım dengelenir; yalnızca isim, telefon ya da
     * araç bilgisi değiştiyse kimsenin servisine dokunulmaz.
     */
    @Transactional
    public DriverDto update(Long id, DriverRequest request) {
        validate(request);

        Driver driver = requireDriver(id);
        double previousLat = driver.getLatitude();
        double previousLon = driver.getLongitude();

        ServiceVehicle vehicle = driver.getServiceVehicle();
        requireUniquePlate(request.plaka(), vehicle != null ? vehicle.getId() : null);

        applyRequest(driver, request);
        driverRepository.save(driver);

        if (vehicle != null) {
            applyVehicle(vehicle, request);
            serviceVehicleRepository.save(vehicle);
        }

        boolean moved = GeoUtils.haversineKm(
                previousLat, previousLon,
                driver.getLatitude(), driver.getLongitude()) > REBALANCE_THRESHOLD_KM;

        if (moved && vehicle != null) {
            assignmentService.rebalance();
        }

        return toDto(driver);
    }

    /**
     * Şoförü ve sürdüğü servisi siler; o servisin yolcularını kalan servislere
     * dağıtır.
     *
     * @throws IllegalStateException kalan servislerin kapasitesi bütün
     *                               personeli taşımaya yetmiyorsa
     */
    @Transactional
    public DriverDeletionDto delete(Long id) {
        Driver driver = requireDriver(id);
        ServiceVehicle vehicle = driver.getServiceVehicle();

        List<Worker> passengers = vehicle == null
                ? List.of()
                : workerRepository.findByServiceVehicleId(vehicle.getId());

        if (!passengers.isEmpty()) {
            requireCapacityWithout(vehicle);

            // Personel servise yabancı anahtarla bağlı; araç silinmeden önce boşa alınmalı.
            passengers.forEach(worker -> worker.setServiceVehicle(null));
            workerRepository.saveAllAndFlush(passengers);
        }

        // Araç, Driver.serviceVehicle üzerindeki CascadeType.REMOVE ile birlikte siliniyor.
        driverRepository.delete(driver);
        driverRepository.flush();

        if (!passengers.isEmpty()) {
            assignmentService.assignUnassigned();
        }

        return new DriverDeletionDto(passengers.size());
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

    private void applyRequest(Driver driver, DriverRequest request) {
        double[] coordinates = locationResolver.resolve(
                request.lat(), request.lon(), request.ilce(), request.adres());

        String[] parts = AddressUtils.splitFullName(request.adSoyad());
        driver.setName(parts[0]);
        driver.setSurname(parts[1]);
        driver.setAddress(request.adres().trim());

        // Personeldeki gibi: boş gönderilen telefon kayıtlı olanı silmez.
        if (request.telefon() != null && !request.telefon().isBlank()) {
            driver.setPhone(request.telefon().trim());
        }
        driver.setLatitude(coordinates[0]);
        driver.setLongitude(coordinates[1]);
    }

    private static void applyVehicle(ServiceVehicle vehicle, DriverRequest request) {
        vehicle.setPlateNumber(normalizePlate(request.plaka()));
        vehicle.setModel(trimmedOrNull(request.model()));
    }

    /**
     * "34 srv  101 " -> "34 SRV 101". Plakalarda Türkçe harf olmadığı için büyük
     * harfe Türkçe kuralıyla değil ROOT ile çevriliyor ("i" -> "İ" olmasın).
     */
    private static String normalizePlate(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private void requireUniquePlate(String rawPlate, Long ownVehicleId) {
        String plate = normalizePlate(rawPlate);
        boolean taken = ownVehicleId == null
                ? serviceVehicleRepository.existsByPlateNumberIgnoreCase(plate)
                : serviceVehicleRepository.existsByPlateNumberIgnoreCaseAndIdNot(plate, ownVehicleId);

        if (taken) {
            throw new IllegalStateException("\"" + plate + "\" plakası başka bir serviste kayıtlı.");
        }
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Driver requireDriver(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Şoför bulunamadı: " + id));
    }

    private void requireCapacityWithout(ServiceVehicle removed) {
        int remainingCapacity = serviceVehicleRepository.findAllByOrderByIdAsc().stream()
                .filter(vehicle -> !vehicle.getId().equals(removed.getId()))
                .mapToInt(ServiceVehicle::getCapacity)
                .sum();
        long totalWorkers = workerRepository.count();

        if (totalWorkers > remainingCapacity) {
            throw new IllegalStateException(
                    "Kalan servislerin toplam kapasitesi (" + remainingCapacity
                            + ") bütün personeli (" + totalWorkers
                            + ") taşımaya yetmiyor. Önce yeni bir şoför ekleyin.");
        }
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
        if (normalizePlate(request.plaka()).isEmpty()) {
            throw new IllegalArgumentException("Plaka zorunlu; servis arayüzde plakasıyla anılıyor.");
        }
    }
}
