package com.eneshasanbese.service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eneshasanbese.dto.EmployeeDto;
import com.eneshasanbese.dto.EmployeeRequest;
import com.eneshasanbese.dto.MutationResultDto;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.enums.Gender;
import com.eneshasanbese.repository.WorkerRepository;
import com.eneshasanbese.util.AddressUtils;
import com.eneshasanbese.util.GeoUtils;

/**
 * Personel CRUD. Ekleme, güncelleme ve silme işlemleri etkilenen servisin
 * rotasını senkron olarak yeniden hesaplayıp yanıtta döndürür — arayüz React
 * Query cache'ini bu yanıtla günceller.
 */
@Service
public class EmployeeService {

    private static final int MIN_AGE = 18;
    private static final int MAX_AGE = 70;
    /** Adres bu mesafeden fazla oynadıysa kişinin servisi yeniden seçilir. */
    private static final double REASSIGN_THRESHOLD_KM = 0.15;

    private final WorkerRepository workerRepository;
    private final AssignmentService assignmentService;
    private final ServiceCatalogService serviceCatalogService;

    public EmployeeService(
            WorkerRepository workerRepository,
            AssignmentService assignmentService,
            ServiceCatalogService serviceCatalogService) {
        this.workerRepository = workerRepository;
        this.assignmentService = assignmentService;
        this.serviceCatalogService = serviceCatalogService;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> findAll() {
        return workerRepository.findAll().stream()
                .sorted(Comparator.comparing(Worker::getId))
                .map(EmployeeService::toDto)
                .toList();
    }

    @Transactional
    public MutationResultDto create(EmployeeRequest request) {
        validate(request);

        Worker worker = new Worker();
        applyRequest(worker, request);
        workerRepository.save(worker);

        ServiceVehicle vehicle = assignmentService.assignOne(worker);
        return serviceCatalogService.mutationResult(toDto(worker), vehicle.getId());
    }

    @Transactional
    public MutationResultDto update(Long id, EmployeeRequest request) {
        validate(request);

        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Personel bulunamadı: " + id));

        double previousLat = worker.getLatitude();
        double previousLon = worker.getLongitude();
        applyRequest(worker, request);
        workerRepository.save(worker);

        boolean moved = GeoUtils.haversineKm(
                previousLat, previousLon,
                worker.getLatitude(), worker.getLongitude()) > REASSIGN_THRESHOLD_KM;

        ServiceVehicle vehicle = (moved || worker.getServiceVehicle() == null)
                ? assignmentService.assignOne(worker)
                : worker.getServiceVehicle();

        return serviceCatalogService.mutationResult(toDto(worker), vehicle.getId());
    }

    @Transactional
    public MutationResultDto delete(Long id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Personel bulunamadı: " + id));

        ServiceVehicle vehicle = worker.getServiceVehicle();
        workerRepository.delete(worker);

        if (vehicle == null) {
            throw new IllegalStateException(
                    "Personel bir servise atanmamıştı; yeniden hesaplanacak rota yok.");
        }
        return serviceCatalogService.mutationResult(null, vehicle.getId());
    }

    // ------------------------------------------------------------- eşleme

    public static EmployeeDto toDto(Worker worker) {
        ServiceVehicle vehicle = worker.getServiceVehicle();

        return new EmployeeDto(
                worker.getId(),
                worker.getName() + " " + worker.getSurname(),
                worker.getGender() == Gender.KADIN ? "Kadın" : "Erkek",
                worker.getAge() != null ? worker.getAge() : 0,
                worker.getAddress(),
                AddressUtils.extractDistrict(worker.getAddress()),
                worker.getLatitude(),
                worker.getLongitude(),
                worker.isHasCar(),
                worker.isHasChild(),
                vehicle != null ? vehicle.getId() : null);
    }

    private void applyRequest(Worker worker, EmployeeRequest request) {
        String[] parts = AddressUtils.splitFullName(request.adSoyad());
        worker.setName(parts[0]);
        worker.setSurname(parts[1]);
        worker.setAddress(request.adres().trim());
        worker.setGender(parseGender(request.cinsiyet()));
        worker.setAge(request.yas());
        worker.setHasCar(Boolean.TRUE.equals(request.arabaliMi()));
        worker.setHasChild(Boolean.TRUE.equals(request.cocukVarMi()));

        if (request.telefon() != null && !request.telefon().isBlank()) {
            worker.setPhone(request.telefon().trim());
        }

        double[] coordinates = resolveCoordinates(request);
        worker.setLatitude(coordinates[0]);
        worker.setLongitude(coordinates[1]);
    }

    /**
     * Arayüz adres autocomplete'inden seçim yapıldıysa koordinat gövdede gelir.
     * Kullanıcı adresi elle yazdıysa, aynı ilçedeki mevcut personelin ağırlık
     * merkezi kullanılır — harici bir geocoding servisine bağımlılık eklememek
     * için kasıtlı olarak böyle.
     */
    private double[] resolveCoordinates(EmployeeRequest request) {
        if (request.lat() != null && request.lon() != null
                && Double.isFinite(request.lat()) && Double.isFinite(request.lon())) {
            return new double[] { request.lat(), request.lon() };
        }

        String district = (request.ilce() != null && !request.ilce().isBlank())
                ? request.ilce().trim()
                : AddressUtils.extractDistrict(request.adres());

        if (district.isBlank()) {
            throw new IllegalArgumentException(
                    "Adresin koordinatı belirlenemedi. Adres listesinden bir sonuç seçin.");
        }

        List<Worker> sameDistrict = workerRepository.findAll().stream()
                .filter(w -> district.equalsIgnoreCase(AddressUtils.extractDistrict(w.getAddress())))
                .toList();

        if (sameDistrict.isEmpty()) {
            throw new IllegalArgumentException(
                    "\"" + district + "\" ilçesi için referans konum yok. Adres listesinden bir sonuç seçin.");
        }

        double lat = sameDistrict.stream().mapToDouble(Worker::getLatitude).average().orElseThrow();
        double lon = sameDistrict.stream().mapToDouble(Worker::getLongitude).average().orElseThrow();
        return new double[] { lat, lon };
    }

    private static Gender parseGender(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return switch (normalized) {
            case "KADIN", "KADİN" -> Gender.KADIN;
            case "ERKEK" -> Gender.ERKEK;
            default -> throw new IllegalArgumentException(
                    "Geçersiz cinsiyet: " + value + " (beklenen: Kadın | Erkek)");
        };
    }

    private void validate(EmployeeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("İstek gövdesi boş.");
        }
        if (request.adSoyad() == null || request.adSoyad().trim().length() < 3) {
            throw new IllegalArgumentException("Ad soyad en az 3 karakter olmalı.");
        }
        if (request.adres() == null || request.adres().trim().length() < 5) {
            throw new IllegalArgumentException("Adres en az 5 karakter olmalı.");
        }
        if (request.yas() == null || request.yas() < MIN_AGE || request.yas() > MAX_AGE) {
            throw new IllegalArgumentException("Yaş " + MIN_AGE + "-" + MAX_AGE + " arasında olmalı.");
        }
        parseGender(request.cinsiyet());
    }
}
