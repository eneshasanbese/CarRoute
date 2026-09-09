package com.eneshasanbese.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.dto.RouteStopDto;
import com.eneshasanbese.dto.ServiceDto;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.enums.Shift;
import com.eneshasanbese.service.RouteService.RouteResult;

/**
 * İş kurallarının testi. OSRM ve trafik verisi sabitleniyor ki sonuçlar
 * dış servislere ve veritabanına bağlı olmasın — ölçülen şey kuralın kendisi.
 */
class RouteServiceRuleTest {

    /** Sabit hız: 60 km/sa, yani 1 km = 1 dakika. Aritmetik elle takip edilebilir. */
    private static final double SABIT_HIZ = 60.0;

    private RouteService routeService(int maxRideMinutes) {
        RouteSettings settings = new TestSettings(maxRideMinutes);
        return new RouteService(new TestTrafficSpeedService(settings), kapaliOsrm(), settings);
    }

    /** OSRM kapalı: hem matris hem rota boş döner, hesap kuş uçuşuna düşer. */
    private OsrmClient kapaliOsrm() {
        return new OsrmClient(false, false, "http://localhost:5000", 100);
    }

    // ------------------------------------------------------------ senaryo

    private ServiceVehicle vehicle() {
        ServiceVehicle vehicle = new ServiceVehicle();
        vehicle.setId(1L);
        vehicle.setCapacity(15);
        return vehicle;
    }

    private Driver driver() {
        Driver driver = new Driver();
        driver.setName("Test");
        driver.setSurname("Şoför");
        driver.setLatitude(41.10);
        driver.setLongitude(29.20);
        return driver;
    }

    /** Ofisten kuzeye doğru sıralanmış üç işçi. */
    private List<Worker> workers() {
        return List.of(
                worker(1L, "Uzak", 41.08, 29.20),
                worker(2L, "Orta", 41.05, 29.20),
                worker(3L, "Yakin", 41.02, 29.20));
    }

    private Worker worker(Long id, String name, double lat, double lon) {
        Worker worker = new Worker();
        worker.setId(id);
        worker.setName(name);
        worker.setSurname("Personel");
        worker.setLatitude(lat);
        worker.setLongitude(lon);
        return worker;
    }

    // -------------------------------------------------------------- test

    @Test
    @DisplayName("Sabah: ilk binen en uzun yolculuğu yapar, son binen en kısa")
    void sabahIlkBinenEnUzun() {
        RouteResult result = routeService(90)
                .build(vehicle(), driver(), workers(), Shift.SABAH);

        List<RouteStopDto> yolcular = result.stops().stream()
                .filter(stop -> stop.employeeId() != null)
                .toList();

        assertEquals(3, yolcular.size());

        for (int i = 1; i < yolcular.size(); i++) {
            assertTrue(
                    yolcular.get(i).yolculukDk() < yolcular.get(i - 1).yolculukDk(),
                    "sonra binen daha kısa yolculuk yapmalı");
        }

        assertEquals(yolcular.get(0).yolculukDk(), result.maxRideMinutes(),
                "kuralı zorlayan kişi ilk binen olmalı");
    }

    @Test
    @DisplayName("Akşam: en son inen en uzun yolculuğu yapar")
    void aksamSonInenEnUzun() {
        RouteResult result = routeService(90)
                .build(vehicle(), driver(), workers(), Shift.AKSAM);

        List<RouteStopDto> yolcular = result.stops().stream()
                .filter(stop -> stop.employeeId() != null)
                .toList();

        for (int i = 1; i < yolcular.size(); i++) {
            assertTrue(
                    yolcular.get(i).yolculukDk() > yolcular.get(i - 1).yolculukDk(),
                    "sonra inen daha uzun yolculuk yapmalı");
        }

        assertEquals(yolcular.get(yolcular.size() - 1).yolculukDk(), result.maxRideMinutes(),
                "kuralı zorlayan kişi en son inen olmalı");
    }

    @Test
    @DisplayName("Sabah varış 08:00'e sabit, kalkış geriye sayılır")
    void sabahVarisSabit() {
        RouteService service = routeService(90);
        RouteResult result = service.build(vehicle(), driver(), workers(), Shift.SABAH);

        assertEquals("08:00", result.endTime());

        LocalTime kalkis = LocalTime.parse(result.startTime());
        assertEquals(LocalTime.of(8, 0).minusMinutes(Math.round(result.totalMinutes())), kalkis);
    }

    @Test
    @DisplayName("Akşam kalkış 17:30'a sabit, varış ileriye sayılır")
    void aksamKalkisSabit() {
        RouteResult result = routeService(90)
                .build(vehicle(), driver(), workers(), Shift.AKSAM);

        assertEquals("17:30", result.startTime());
        assertTrue(LocalTime.parse(result.endTime()).isAfter(LocalTime.of(17, 30)));
    }

    @Test
    @DisplayName("Sınırın altında ihlal yok, üstünde ihlal var")
    void kuralSiniri() {
        ServiceVehicle vehicle = vehicle();

        RouteService gevsek = routeService(600);
        ServiceDto gevsekDto = gevsek.toService(
                vehicle, gevsek.build(vehicle, driver(), workers(), Shift.SABAH), 3, Shift.SABAH);
        assertFalse(gevsekDto.kuralIhlali(), "600 dakikalık sınır aşılmamalı");

        RouteService siki = routeService(1);
        ServiceDto sikiDto = siki.toService(
                vehicle, siki.build(vehicle, driver(), workers(), Shift.SABAH), 3, Shift.SABAH);
        assertTrue(sikiDto.kuralIhlali(), "1 dakikalık sınır aşılmalı");
        assertEquals(1, sikiDto.azamiYolculukDk());
    }

    @Test
    @DisplayName("Şoför kurala dahil değil: turun tamamı en uzun yolculuktan uzun")
    void soforKuralaDahilDegil() {
        RouteResult result = routeService(90)
                .build(vehicle(), driver(), workers(), Shift.SABAH);

        assertTrue(result.totalMinutes() > result.maxRideMinutes(),
                "şoför herkesten uzun yolda ama kurala girmiyor");
    }

    @Test
    @DisplayName("Varış penceresi ilerledikçe genişler")
    void varisPenceresiGenisler() {
        RouteResult result = routeService(90)
                .build(vehicle(), driver(), workers(), Shift.SABAH);

        List<RouteStopDto> stops = result.stops();

        // Kalkış durağında belirsizlik yok.
        assertEquals(stops.get(0).varisSaatiErken(), stops.get(0).varisSaatiGec());

        long ilkPencere = pencereDakika(stops.get(1));
        long sonPencere = pencereDakika(stops.get(stops.size() - 1));
        assertTrue(sonPencere >= ilkPencere, "belirsizlik yol aldıkça birikmeli");
    }

    private long pencereDakika(RouteStopDto stop) {
        return java.time.Duration.between(
                LocalTime.parse(stop.varisSaatiErken()),
                LocalTime.parse(stop.varisSaatiGec())).toMinutes();
    }

    // ------------------------------------------------------------ sahteler

    /** Her yerde aynı hız, aynı oynaklık — rota aritmetiği izole kalsın. */
    private static class TestTrafficSpeedService extends TrafficSpeedService {

        TestTrafficSpeedService(RouteSettings settings) {
            super(null, settings);
        }

        @Override
        public double speedKmh(double lat, double lon, String timeSlot) {
            return SABIT_HIZ;
        }

        @Override
        public double legSpeedKmh(double a, double b, double c, double d, String timeSlot) {
            return SABIT_HIZ;
        }

        @Override
        public double congestionFactor(double lat, double lon, String timeSlot) {
            return 1.0;
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

        private final int maxRideMinutes;

        TestSettings(int maxRideMinutes) {
            this.maxRideMinutes = maxRideMinutes;
        }

        @Override
        public double getOfficeLat() {
            return 41.00;
        }

        @Override
        public double getOfficeLon() {
            return 29.20;
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
            return 5;
        }

        @Override
        public int getMaxRideMinutes() {
            return maxRideMinutes;
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
