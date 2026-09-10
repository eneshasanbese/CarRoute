package com.eneshasanbese.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.repository.DriverRepository;
import com.eneshasanbese.repository.ServiceVehicleRepository;
import com.eneshasanbese.repository.WorkerRepository;

/**
 * Atama katmanının davranış testleri.
 *
 * <p>
 * Coğrafya bilerek keskin: ofis merkezde, bir servis kuzey koridorunu, diğeri
 * doğu koridorunu topluyor. Doğru cevap bakınca belli olduğu için test, "daha
 * iyi mi oldu" gibi bulanık bir şey değil, <b>kim nereye düştü</b> diye
 * sorabiliyor.
 *
 * <p>
 * OSRM kapalı: mesafe kuş uçuşu, hız sabit. Ölçülen şey atama mantığı, dış
 * servisin doğruluğu değil.
 */
class AssignmentServiceTest {

    private static final double OFFICE_LAT = 41.00;
    private static final double OFFICE_LON = 29.00;
    private static final int MIN_CAPACITY = 5;

    private final List<Worker> workers = new ArrayList<>();
    private final List<Driver> drivers = new ArrayList<>();
    private final List<ServiceVehicle> vehicles = new ArrayList<>();

    // ------------------------------------------------------------- testler

    @Test
    @DisplayName("Coğrafi kümeler kendi servisine düşer")
    void kumelerDogruServiseDuser() {
        AssignmentService service = scenario(4.0);
        addNorthCluster(6);
        addEastCluster(6);

        service.reassignAll();

        for (Worker worker : workers) {
            long expected = worker.getLongitude() > OFFICE_LON + 0.01 ? 2L : 1L;
            assertEquals(expected, worker.getServiceVehicle().getId(),
                    worker.getName() + " yanlış servise düştü");
        }
    }

    @Test
    @DisplayName("Aynı veriyle iki dağıtım aynı sonucu verir")
    void dagitimTekrarlanabilir() {
        AssignmentService service = scenario(4.0);
        addNorthCluster(7);
        addEastCluster(7);

        service.reassignAll();
        List<Long> first = assignmentSnapshot();

        service.reassignAll();

        assertEquals(first, assignmentSnapshot(),
                "dağıtım tekrarlanabilir olmalı — sunumda iki çalıştırma iki cevap veremez");
    }

    @Test
    @DisplayName("Takas, taşımanın çözemediği karşılıklı yanlış atamayı düzeltir")
    void takasKarsilikliYanlisAtamayiDuzeltir() {
        AssignmentService service = scenario(4.0);

        // Kuzey servisi bir doğulu taşıyor, doğu servisi bir kuzeyli.
        List<Worker> north = addNorthCluster(4);
        List<Worker> east = addEastCluster(4);
        Worker misplacedEast = east(4);
        Worker misplacedNorth = north(4);

        vehicles.get(0).setCapacity(6);
        vehicles.get(1).setCapacity(5);

        north.forEach(w -> w.setServiceVehicle(vehicles.get(0)));
        misplacedEast.setServiceVehicle(vehicles.get(0));
        east.forEach(w -> w.setServiceVehicle(vehicles.get(1)));
        misplacedNorth.setServiceVehicle(vehicles.get(1));

        // Tek kişilik taşıma burada çalışamaz: kaynak servisler asgari doluluğa,
        // hedef servisler kapasiteye dayalı. Kalan tek hamle takas.
        Worker newcomer = north(5);

        service.assignUnassigned();

        assertEquals(2L, misplacedEast.getServiceVehicle().getId(),
                "doğuda oturan kişi doğu servisine geçmeliydi");
        assertEquals(1L, misplacedNorth.getServiceVehicle().getId(),
                "kuzeyde oturan kişi kuzey servisine geçmeliydi");
        assertNotNull(newcomer.getServiceVehicle());
    }

    @Test
    @DisplayName("Asgari doluluk korunur")
    void asgariDolulukKorunur() {
        AssignmentService service = scenario(4.0);
        addNorthCluster(11);
        addEastCluster(5);

        service.reassignAll();

        for (ServiceVehicle vehicle : vehicles) {
            long count = workers.stream()
                    .filter(w -> vehicle.getId().equals(w.getServiceVehicle().getId()))
                    .count();
            assertTrue(count >= MIN_CAPACITY,
                    "Servis-" + vehicle.getId() + " asgari doluluğun altında: " + count);
        }
    }

    @Test
    @DisplayName("Kapasite aşılmaz")
    void kapasiteAsilmaz() {
        AssignmentService service = scenario(4.0);
        addNorthCluster(9);
        addEastCluster(9);
        vehicles.forEach(v -> v.setCapacity(10));

        service.reassignAll();

        for (ServiceVehicle vehicle : vehicles) {
            long count = workers.stream()
                    .filter(w -> vehicle.getId().equals(w.getServiceVehicle().getId()))
                    .count();
            assertTrue(count <= vehicle.getCapacity(),
                    "Servis-" + vehicle.getId() + " kapasiteyi aştı: " + count);
        }
    }

    // ------------------------------------------------------------ senaryo

    /** Ofis merkezde, iki servis iki dik koridoru topluyor. */
    private AssignmentService scenario(double penaltyWeight) {
        vehicles.add(vehicle(1L));
        vehicles.add(vehicle(2L));
        drivers.add(driver(vehicles.get(0), 41.20, OFFICE_LON));
        drivers.add(driver(vehicles.get(1), OFFICE_LAT, 29.25));

        RouteSettings settings = new TestSettings(penaltyWeight);
        RouteService routeService = new RouteService(
                new FixedSpeedTrafficService(settings),
                new OsrmClient(false, false, "http://localhost:5000", 100),
                settings);

        return new AssignmentService(
                workerRepository(), driverRepository(), vehicleRepository(), routeService, settings);
    }

    private List<Worker> addNorthCluster(int count) {
        List<Worker> added = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            added.add(north(i));
        }
        return added;
    }

    private List<Worker> addEastCluster(int count) {
        List<Worker> added = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            added.add(east(i));
        }
        return added;
    }

    private Worker north(int index) {
        return worker("Kuzey" + index, OFFICE_LAT + 0.02 + index * 0.015, OFFICE_LON);
    }

    private Worker east(int index) {
        return worker("Dogu" + index, OFFICE_LAT, OFFICE_LON + 0.02 + index * 0.015);
    }

    private Worker worker(String name, double lat, double lon) {
        Worker worker = new Worker();
        worker.setId((long) (workers.size() + 1));
        worker.setName(name);
        worker.setSurname("Personel");
        worker.setLatitude(lat);
        worker.setLongitude(lon);
        workers.add(worker);
        return worker;
    }

    private ServiceVehicle vehicle(Long id) {
        ServiceVehicle vehicle = new ServiceVehicle();
        vehicle.setId(id);
        vehicle.setCapacity(15);
        return vehicle;
    }

    private Driver driver(ServiceVehicle vehicle, double lat, double lon) {
        Driver driver = new Driver();
        driver.setId(vehicle.getId());
        driver.setName("Sofor" + vehicle.getId());
        driver.setSurname("Test");
        driver.setLatitude(lat);
        driver.setLongitude(lon);
        driver.setServiceVehicle(vehicle);
        return driver;
    }

    private List<Long> assignmentSnapshot() {
        return workers.stream()
                .sorted(Comparator.comparing(Worker::getId))
                .map(w -> w.getServiceVehicle().getId())
                .toList();
    }

    // -------------------------------------------------------------- sahte

    private WorkerRepository workerRepository() {
        WorkerRepository repository = mock(WorkerRepository.class);
        // Sıralı sorgular: dağıtımın tekrarlanabilirliği buna dayanıyor.
        when(repository.findAllByOrderByIdAsc()).thenAnswer(call -> sortedWorkers());
        when(repository.findByServiceVehicleIsNullOrderByIdAsc()).thenAnswer(call -> sortedWorkers().stream()
                .filter(w -> w.getServiceVehicle() == null)
                .toList());
        when(repository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(repository.save(org.mockito.ArgumentMatchers.any(Worker.class)))
                .thenAnswer(call -> call.getArgument(0));
        return repository;
    }

    private List<Worker> sortedWorkers() {
        return workers.stream().sorted(Comparator.comparing(Worker::getId)).toList();
    }

    private DriverRepository driverRepository() {
        DriverRepository repository = mock(DriverRepository.class);
        when(repository.findAll()).thenAnswer(call -> List.copyOf(drivers));
        return repository;
    }

    private ServiceVehicleRepository vehicleRepository() {
        ServiceVehicleRepository repository = mock(ServiceVehicleRepository.class);
        when(repository.findAllByOrderByIdAsc()).thenAnswer(call -> List.copyOf(vehicles));
        return repository;
    }

    /** Her yerde aynı hız — atama mantığı trafik verisinden yalıtılsın. */
    private static class FixedSpeedTrafficService extends TrafficSpeedService {

        FixedSpeedTrafficService(RouteSettings settings) {
            super(null, settings);
        }

        @Override
        public double speedKmh(double lat, double lon, String timeSlot) {
            return 60.0;
        }

        @Override
        public double legSpeedKmh(double a, double b, double c, double d, String timeSlot) {
            return 60.0;
        }

        @Override
        public boolean hasFreeFlowData() {
            return false;
        }

        @Override
        public double speedVariation(double lat, double lon, String timeSlot) {
            return 0.10;
        }
    }

    private static class TestSettings extends RouteSettings {

        private final double penaltyWeight;

        TestSettings(double penaltyWeight) {
            this.penaltyWeight = penaltyWeight;
        }

        @Override
        public double getOfficeLat() {
            return OFFICE_LAT;
        }

        @Override
        public double getOfficeLon() {
            return OFFICE_LON;
        }

        @Override
        public String getOfficeLabel() {
            return "Ofis";
        }

        @Override
        public double getRoadFactor() {
            return 1.0;
        }

        @Override
        public double getBoardingMinutes() {
            return 1.0;
        }

        @Override
        public int getMinCapacity() {
            return MIN_CAPACITY;
        }

        @Override
        public int getMaxRideMinutes() {
            return 90;
        }

        @Override
        public double getRulePenaltyWeight() {
            return penaltyWeight;
        }

        @Override
        public double getDefaultVariation() {
            return 0.10;
        }

        @Override
        public LocalTime arrivalAt() {
            return LocalTime.of(8, 0);
        }

        @Override
        public LocalTime departureAt() {
            return LocalTime.of(17, 30);
        }
    }
}
