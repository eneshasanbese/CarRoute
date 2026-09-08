package com.eneshasanbese.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.dto.RouteStopDto;
import com.eneshasanbese.dto.ServiceDto;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.util.GeoUtils;

/**
 * Tek bir servisin güzergâhını kurar.
 *
 * <p>
 * Rota şoförün ev adresinden başlar, atanmış işçileri sırayla toplar ve ofiste
 * biter (sabit garaj yok — seed dosyasındaki senaryo bu). Sıralama önce
 * en-yakın-komşu ile kurulur, ardından 2-opt ile iyileştirilir; iki uç nokta
 * (şoför evi ve ofis) sabit tutulur.
 *
 * <p>
 * Bacak maliyeti mesafe değil <em>süre</em>: kuş uçuşu mesafe karayolu çarpanıyla
 * düzeltilir ve o bölgenin sabah zirvesi ortalama hızına bölünür. Böylece trafiği
 * yoğun bir koridordan geçen kısa bir bacak, boş bir koridordan geçen uzun bacaktan
 * pahalı olabilir.
 */
@Service
public class RouteService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int TWO_OPT_MAX_PASSES = 50;

    private final TrafficSpeedService trafficSpeedService;
    private final RouteSettings settings;

    public RouteService(TrafficSpeedService trafficSpeedService, RouteSettings settings) {
        this.trafficSpeedService = trafficSpeedService;
        this.settings = settings;
    }

    /** Sıralanmış işçiler + toplam mesafe/süre. */
    public record RoutePlan(List<Worker> orderedWorkers, double totalKm, double totalMinutes) {
    }

    public RoutePlan plan(Driver driver, List<Worker> workers) {
        double startLat = driver != null ? driver.getLatitude() : settings.getOfficeLat();
        double startLon = driver != null ? driver.getLongitude() : settings.getOfficeLon();

        List<Worker> ordered = nearestNeighbour(startLat, startLon, workers);
        twoOpt(startLat, startLon, ordered);

        return measure(startLat, startLon, ordered);
    }

    /**
     * Atama denemelerinde kullanılan hafif maliyet: 2-opt çalıştırılmadan yalnızca
     * en-yakın-komşu turunun süresi. Yüzlerce deneme yapıldığı için ucuz olması
     * gerekiyor; nihai rota her zaman {@link #plan} ile hesaplanır.
     */
    public double estimateMinutes(Driver driver, List<Worker> workers) {
        double startLat = driver != null ? driver.getLatitude() : settings.getOfficeLat();
        double startLon = driver != null ? driver.getLongitude() : settings.getOfficeLon();

        List<Worker> ordered = nearestNeighbour(startLat, startLon, workers);
        return measure(startLat, startLon, ordered).totalMinutes();
    }

    public List<RouteStopDto> toStops(ServiceVehicle vehicle, Driver driver, RoutePlan plan) {
        double startLat = driver != null ? driver.getLatitude() : settings.getOfficeLat();
        double startLon = driver != null ? driver.getLongitude() : settings.getOfficeLon();
        String startLabel = driver != null
                ? "Kalkış: " + driver.getName() + " " + driver.getSurname()
                : "Kalkış noktası";

        List<RouteStopDto> stops = new ArrayList<>();
        stops.add(new RouteStopDto(vehicle.getId(), 0, null, startLabel, startLat, startLon, 0));

        double previousLat = startLat;
        double previousLon = startLon;
        int stopNumber = 1;

        for (Worker worker : plan.orderedWorkers()) {
            double km = roadKm(previousLat, previousLon, worker.getLatitude(), worker.getLongitude());
            stops.add(new RouteStopDto(
                    vehicle.getId(),
                    stopNumber++,
                    worker.getId(),
                    worker.getName() + " " + worker.getSurname(),
                    worker.getLatitude(),
                    worker.getLongitude(),
                    round2(km)));
            previousLat = worker.getLatitude();
            previousLon = worker.getLongitude();
        }

        double lastLegKm = roadKm(previousLat, previousLon, settings.getOfficeLat(), settings.getOfficeLon());
        stops.add(new RouteStopDto(
                vehicle.getId(),
                stopNumber,
                null,
                settings.getOfficeLabel(),
                settings.getOfficeLat(),
                settings.getOfficeLon(),
                round2(lastLegKm)));

        return stops;
    }

    public ServiceDto toService(ServiceVehicle vehicle, RoutePlan plan) {
        int durationMinutes = (int) Math.round(plan.totalMinutes());

        return new ServiceDto(
                vehicle.getId(),
                plan.orderedWorkers().size(),
                settings.getMinCapacity(),
                vehicle.getCapacity(),
                round1(plan.totalKm()),
                durationMinutes,
                departureTime(durationMinutes));
    }

    /** Ofiste 08:00'de olacak şekilde geriye sayılan kalkış saati. */
    public String departureTime(int durationMinutes) {
        return settings.arrivalAt().minusMinutes(durationMinutes).format(HHMM);
    }

    // ---------------------------------------------------------------- sıralama

    private List<Worker> nearestNeighbour(double startLat, double startLon, List<Worker> workers) {
        List<Worker> remaining = new ArrayList<>(workers);
        List<Worker> ordered = new ArrayList<>(remaining.size());

        double currentLat = startLat;
        double currentLon = startLon;

        while (!remaining.isEmpty()) {
            int bestIndex = 0;
            double bestCost = Double.MAX_VALUE;

            for (int i = 0; i < remaining.size(); i++) {
                Worker candidate = remaining.get(i);
                double cost = legMinutes(currentLat, currentLon, candidate.getLatitude(), candidate.getLongitude());
                if (cost < bestCost) {
                    bestCost = cost;
                    bestIndex = i;
                }
            }

            Worker chosen = remaining.remove(bestIndex);
            ordered.add(chosen);
            currentLat = chosen.getLatitude();
            currentLon = chosen.getLongitude();
        }

        return ordered;
    }

    /** Uçları sabit 2-opt: [şoför] -> işçiler -> [ofis] dizisinde kesişmeleri açar. */
    private void twoOpt(double startLat, double startLon, List<Worker> ordered) {
        if (ordered.size() < 3) {
            return;
        }

        boolean improved = true;
        int pass = 0;

        while (improved && pass++ < TWO_OPT_MAX_PASSES) {
            improved = false;

            for (int i = 0; i < ordered.size() - 1; i++) {
                for (int j = i + 1; j < ordered.size(); j++) {
                    double before = segmentCost(startLat, startLon, ordered, i, j);
                    Collections.reverse(ordered.subList(i, j + 1));
                    double after = segmentCost(startLat, startLon, ordered, i, j);

                    if (after < before - 1e-9) {
                        improved = true;
                    } else {
                        Collections.reverse(ordered.subList(i, j + 1));
                    }
                }
            }
        }
    }

    /**
     * 2-opt hamlesinin etkilediği tek şey i'den önceki ve j'den sonraki bağlantı;
     * ters çevrilen aradaki bacakların toplamı simetrik olduğu için değişmez.
     */
    private double segmentCost(double startLat, double startLon, List<Worker> ordered, int i, int j) {
        double[] previous = i == 0
                ? new double[] { startLat, startLon }
                : new double[] { ordered.get(i - 1).getLatitude(), ordered.get(i - 1).getLongitude() };

        double[] next = j == ordered.size() - 1
                ? new double[] { settings.getOfficeLat(), settings.getOfficeLon() }
                : new double[] { ordered.get(j + 1).getLatitude(), ordered.get(j + 1).getLongitude() };

        Worker first = ordered.get(i);
        Worker last = ordered.get(j);

        return legMinutes(previous[0], previous[1], first.getLatitude(), first.getLongitude())
                + legMinutes(last.getLatitude(), last.getLongitude(), next[0], next[1]);
    }

    private RoutePlan measure(double startLat, double startLon, List<Worker> ordered) {
        double totalKm = 0;
        double totalMinutes = 0;

        double currentLat = startLat;
        double currentLon = startLon;

        for (Worker worker : ordered) {
            totalKm += roadKm(currentLat, currentLon, worker.getLatitude(), worker.getLongitude());
            totalMinutes += legMinutes(currentLat, currentLon, worker.getLatitude(), worker.getLongitude());
            totalMinutes += settings.getBoardingMinutes();
            currentLat = worker.getLatitude();
            currentLon = worker.getLongitude();
        }

        totalKm += roadKm(currentLat, currentLon, settings.getOfficeLat(), settings.getOfficeLon());
        totalMinutes += legMinutes(currentLat, currentLon, settings.getOfficeLat(), settings.getOfficeLon());

        return new RoutePlan(ordered, totalKm, totalMinutes);
    }

    // ---------------------------------------------------------------- maliyet

    private double roadKm(double fromLat, double fromLon, double toLat, double toLon) {
        return GeoUtils.haversineKm(fromLat, fromLon, toLat, toLon) * settings.getRoadFactor();
    }

    private double legMinutes(double fromLat, double fromLon, double toLat, double toLon) {
        double km = roadKm(fromLat, fromLon, toLat, toLon);
        double speed = trafficSpeedService.legSpeedKmh(
                fromLat, fromLon, toLat, toLon, TrafficSpeedService.SLOT_MORNING);
        return km / speed * 60;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
