package com.eneshasanbese.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.dto.RouteDto;
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
 * <b>İki farklı maliyet var, bilerek:</b>
 * <ul>
 * <li><i>Sıralama ve atama</i> kuş uçuşu mesafenin karayolu çarpanıyla
 * düzeltilmiş halini kullanır. Atama sırasında binlerce kez hesaplandığı için
 * ucuz olması şart.</li>
 * <li><i>Sonuçta raporlanan</i> mesafe, süre ve haritaya çizilen çizgi ise
 * OSRM'den gelen <b>gerçek yol</b> verisidir — servis başına tek bir istek.
 * OSRM kapalıysa burası da kuş uçuşu tahmine düşer.</li>
 * </ul>
 *
 * <p>
 * Süre her iki durumda da yol mesafesinin o bölgenin sabah zirvesi ortalama
 * hızına bölünmesiyle bulunur; yani trafik verisi OSRM ile birlikte de
 * kullanılmaya devam eder.
 */
@Service
public class RouteService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final int TWO_OPT_MAX_PASSES = 50;

    private final TrafficSpeedService trafficSpeedService;
    private final OsrmClient osrmClient;
    private final RouteSettings settings;

    public RouteService(
            TrafficSpeedService trafficSpeedService,
            OsrmClient osrmClient,
            RouteSettings settings) {
        this.trafficSpeedService = trafficSpeedService;
        this.osrmClient = osrmClient;
        this.settings = settings;
    }

    /**
     * Hesaplanmış rota.
     *
     * @param geometry yol çizgisi ([lat, lon] noktaları); OSRM yoksa null olur ve
     *                 arayüz durakları düz çizgiyle birleştirir
     */
    public record RouteResult(
            List<RouteStopDto> stops,
            List<double[]> geometry,
            double totalKm,
            double totalMinutes) {
    }

    public RouteResult build(ServiceVehicle vehicle, Driver driver, List<Worker> workers) {
        double startLat = driver != null ? driver.getLatitude() : settings.getOfficeLat();
        double startLon = driver != null ? driver.getLongitude() : settings.getOfficeLon();

        List<Worker> ordered = nearestNeighbour(startLat, startLon, workers);
        twoOpt(startLat, startLon, ordered);

        List<double[]> points = new ArrayList<>();
        points.add(new double[] { startLat, startLon });
        ordered.forEach(w -> points.add(new double[] { w.getLatitude(), w.getLongitude() }));
        points.add(new double[] { settings.getOfficeLat(), settings.getOfficeLon() });

        Optional<OsrmClient.Route> osrmRoute = osrmClient.route(points);
        List<Double> legKm = legDistancesKm(points, osrmRoute);

        return assemble(vehicle, driver, ordered, points, legKm,
                osrmRoute.map(OsrmClient.Route::geometry).orElse(null));
    }

    /**
     * Atama denemelerinde kullanılan hafif maliyet: 2-opt ve OSRM olmadan,
     * yalnızca en-yakın-komşu turunun kuş uçuşu süresi.
     */
    public double estimateMinutes(Driver driver, List<Worker> workers) {
        double startLat = driver != null ? driver.getLatitude() : settings.getOfficeLat();
        double startLon = driver != null ? driver.getLongitude() : settings.getOfficeLon();

        List<Worker> ordered = nearestNeighbour(startLat, startLon, workers);

        double minutes = 0;
        double currentLat = startLat;
        double currentLon = startLon;

        for (Worker worker : ordered) {
            minutes += legMinutes(currentLat, currentLon, worker.getLatitude(), worker.getLongitude(),
                    straightRoadKm(currentLat, currentLon, worker.getLatitude(), worker.getLongitude()));
            minutes += settings.getBoardingMinutes();
            currentLat = worker.getLatitude();
            currentLon = worker.getLongitude();
        }

        minutes += legMinutes(currentLat, currentLon, settings.getOfficeLat(), settings.getOfficeLon(),
                straightRoadKm(currentLat, currentLon, settings.getOfficeLat(), settings.getOfficeLon()));

        return minutes;
    }

    public ServiceDto toService(ServiceVehicle vehicle, RouteResult result, int kisiSayisi) {
        int durationMinutes = (int) Math.round(result.totalMinutes());

        return new ServiceDto(
                vehicle.getId(),
                kisiSayisi,
                settings.getMinCapacity(),
                vehicle.getCapacity(),
                round1(result.totalKm()),
                durationMinutes,
                departureTime(durationMinutes));
    }

    public RouteDto toRoute(RouteResult result) {
        return new RouteDto(result.stops(), result.geometry());
    }

    /** Ofiste 08:00'de olunacak şekilde geriye sayılan kalkış saati. */
    public String departureTime(int durationMinutes) {
        return settings.arrivalAt().minusMinutes(durationMinutes).format(HHMM);
    }

    // ---------------------------------------------------------------- kurgu

    /** Bacak mesafeleri: varsa OSRM'in yol mesafesi, yoksa kuş uçuşu × çarpan. */
    private List<Double> legDistancesKm(List<double[]> points, Optional<OsrmClient.Route> osrmRoute) {
        int legCount = points.size() - 1;

        if (osrmRoute.isPresent() && osrmRoute.get().legs().size() == legCount) {
            return osrmRoute.get().legs().stream()
                    .map(leg -> leg.distanceMeters() / 1000.0)
                    .toList();
        }

        List<Double> distances = new ArrayList<>(legCount);
        for (int i = 0; i < legCount; i++) {
            double[] from = points.get(i);
            double[] to = points.get(i + 1);
            distances.add(straightRoadKm(from[0], from[1], to[0], to[1]));
        }
        return distances;
    }

    private RouteResult assemble(
            ServiceVehicle vehicle,
            Driver driver,
            List<Worker> ordered,
            List<double[]> points,
            List<Double> legKm,
            List<double[]> geometry) {

        String startLabel = driver != null
                ? "Kalkış: " + driver.getName() + " " + driver.getSurname()
                : "Kalkış noktası";

        List<RouteStopDto> stops = new ArrayList<>();
        stops.add(new RouteStopDto(
                vehicle.getId(), 0, null, startLabel, points.get(0)[0], points.get(0)[1], 0));

        double totalKm = 0;
        double totalMinutes = 0;

        for (int i = 0; i < ordered.size(); i++) {
            Worker worker = ordered.get(i);
            double km = legKm.get(i);
            double[] from = points.get(i);

            totalKm += km;
            totalMinutes += legMinutes(from[0], from[1], worker.getLatitude(), worker.getLongitude(), km);
            totalMinutes += settings.getBoardingMinutes();

            stops.add(new RouteStopDto(
                    vehicle.getId(),
                    i + 1,
                    worker.getId(),
                    worker.getName() + " " + worker.getSurname(),
                    worker.getLatitude(),
                    worker.getLongitude(),
                    round2(km)));
        }

        double lastKm = legKm.get(legKm.size() - 1);
        double[] beforeOffice = points.get(points.size() - 2);
        totalKm += lastKm;
        totalMinutes += legMinutes(
                beforeOffice[0], beforeOffice[1],
                settings.getOfficeLat(), settings.getOfficeLon(), lastKm);

        stops.add(new RouteStopDto(
                vehicle.getId(),
                ordered.size() + 1,
                null,
                settings.getOfficeLabel(),
                settings.getOfficeLat(),
                settings.getOfficeLon(),
                round2(lastKm)));

        return new RouteResult(stops, geometry, totalKm, totalMinutes);
    }

    // ------------------------------------------------------------- sıralama

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
                double cost = straightLegMinutes(
                        currentLat, currentLon, candidate.getLatitude(), candidate.getLongitude());
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

        return straightLegMinutes(previous[0], previous[1], first.getLatitude(), first.getLongitude())
                + straightLegMinutes(last.getLatitude(), last.getLongitude(), next[0], next[1]);
    }

    // -------------------------------------------------------------- maliyet

    private double straightRoadKm(double fromLat, double fromLon, double toLat, double toLon) {
        return GeoUtils.haversineKm(fromLat, fromLon, toLat, toLon) * settings.getRoadFactor();
    }

    private double straightLegMinutes(double fromLat, double fromLon, double toLat, double toLon) {
        return legMinutes(fromLat, fromLon, toLat, toLon,
                straightRoadKm(fromLat, fromLon, toLat, toLon));
    }

    /** Verilen yol mesafesini o koridorun sabah zirvesi ortalama hızına böler. */
    private double legMinutes(double fromLat, double fromLon, double toLat, double toLon, double km) {
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
