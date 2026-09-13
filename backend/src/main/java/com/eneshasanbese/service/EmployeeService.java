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
import com.eneshasanbese.enums.Shift;
import com.eneshasanbese.repository.WorkerRepository;
import com.eneshasanbese.util.AddressUtils;
import com.eneshasanbese.util.GeoUtils;

/**
 * Personel CRUD. Ekleme, güncelleme ve silme işlemleri etkilenen servisin
 * rotasını senkron olarak yeniden hesaplayıp yanıtta döndürür — arayüz Redux
 * store'unu bu yanıtla günceller.
 *
 * <p>
 * Üç işlem de dağılımı değiştirdiği için {@link AssignmentLock} ile sıraya
 * giriyor; kilit her okumadan önce alınıyor.
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
    private final LocationResolver locationResolver;
    private final AssignmentLock assignmentLock;

    public EmployeeService(
            WorkerRepository workerRepository,
            AssignmentService assignmentService,
            ServiceCatalogService serviceCatalogService,
            LocationResolver locationResolver,
            AssignmentLock assignmentLock) {
        this.workerRepository = workerRepository;
        this.assignmentService = assignmentService;
        this.serviceCatalogService = serviceCatalogService;
        this.locationResolver = locationResolver;
        this.assignmentLock = assignmentLock;
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto> findAll() {
        return workerRepository.findAll().stream()
                .sorted(Comparator.comparing(Worker::getId))
                .map(EmployeeService::toDto)
                .toList();
    }

    /** @param shift yanıttaki rotanın ait olacağı sefer — arayüzde açık olan */
    @Transactional
    public MutationResultDto create(EmployeeRequest request, Shift shift) {
        validate(request);
        assignmentLock.acquire();

        Worker worker = new Worker();
        applyRequest(worker, request);
        workerRepository.save(worker);

        ServiceVehicle vehicle = assignmentService.assignOne(worker);
        return serviceCatalogService.mutationResult(toDto(worker), vehicle.getId(), shift);
    }

    @Transactional
    public MutationResultDto update(Long id, EmployeeRequest request, Shift shift) {
        validate(request);
        assignmentLock.acquire();

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

        return serviceCatalogService.mutationResult(toDto(worker), vehicle.getId(), shift);
    }

    /**
     * Personeli siler; bir servise atanmışsa o servisin güncel halini döndürür.
     *
     * <p>
     * Servise atanmamış kişi de silinebilmeli. Önceden burada hata
     * fırlatılıyordu ve {@code @Transactional} silmeyi geri alıyordu: kişi
     * silinmiyor, kullanıcı 409 görüyordu. Artık yeniden hesaplanacak rota yoksa
     * yanıtın servis ve rota alanları boş dönüyor.
     */
    @Transactional
    public MutationResultDto delete(Long id, Shift shift) {
        assignmentLock.acquire();

        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Personel bulunamadı: " + id));

        ServiceVehicle vehicle = worker.getServiceVehicle();
        workerRepository.delete(worker);

        if (vehicle == null) {
            return new MutationResultDto(null, null, null);
        }
        return serviceCatalogService.mutationResult(null, vehicle.getId(), shift);
    }

    // ------------------------------------------------------------- eşleme

    public static EmployeeDto toDto(Worker worker) {
        ServiceVehicle vehicle = worker.getServiceVehicle();

        return new EmployeeDto(
                worker.getId(),
                worker.getName() + " " + worker.getSurname(),
                worker.getGender() == Gender.KADIN ? "Kadın" : "Erkek",
                worker.getAge() != null ? worker.getAge() : 0,
                worker.getPhone(),
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

        double[] coordinates = locationResolver.resolve(
                request.lat(), request.lon(), request.ilce(), request.adres());
        worker.setLatitude(coordinates[0]);
        worker.setLongitude(coordinates[1]);
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
