package com.eneshasanbese.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * OSRM (Open Source Routing Machine) istemcisi — duraklar arasındaki gerçek yol
 * güzergâhını ve yol mesafesini verir.
 *
 * <p>
 * Bu olmadan rota, noktaları düz çizgiyle birleştiren bir kuş uçuşu tahminden
 * ibaret kalır. OSRM'e tek bir {@code /route} isteği atarak hem haritaya
 * çizilecek yol geometrisini hem de her bacağın gerçek metre cinsinden
 * uzunluğunu aynı anda alıyoruz.
 *
 * <p>
 * Kapalıysa ya da ulaşılamıyorsa {@link #route} boş döner ve çağıran taraf
 * kuş uçuşu hesaba geri düşer — uygulama OSRM olmadan da çalışır.
 */
@Service
public class OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final RestClient restClient;
    /** Arka arkaya hata alındığında logu doldurmamak için. */
    private volatile boolean warned;

    public OsrmClient(
            @Value("${carroute.osrm.enabled:true}") boolean enabled,
            @Value("${carroute.osrm.base-url:http://localhost:5000}") String baseUrl,
            @Value("${carroute.osrm.timeout-ms:8000}") int timeoutMs) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.replaceAll("/+$", "");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        // RestClient.Builder bean'i webmvc starter ile gelmiyor; kendi örneğimizi
        // kuruyoruz. Varsayılan dönüştürücüler classpath'teki Jackson'ı bulur.
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(factory)
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Bir bacağın yol mesafesi (metre) ve süresi (saniye). */
    public record Leg(double distanceMeters, double durationSeconds) {
    }

    /**
     * Sıralı duraklar üzerinden geçen sürüş rotası.
     *
     * @param geometry harita için [lat, lon] noktaları
     * @param legs     ardışık durak çiftleri arası bacaklar (durak sayısı - 1 adet)
     */
    public record Route(List<double[]> geometry, List<Leg> legs, double totalDistanceMeters) {
    }

    /**
     * @param points sıralı [lat, lon] noktaları (en az 2 tane)
     * @return rota; OSRM kapalı, ulaşılamaz ya da rota bulamadıysa boş
     */
    public Optional<Route> route(List<double[]> points) {
        if (!enabled || points.size() < 2) {
            return Optional.empty();
        }

        // OSRM koordinatları lon,lat sırasıyla ve ";" ile ayrılmış bekler.
        StringBuilder coordinates = new StringBuilder();
        for (double[] point : points) {
            if (!coordinates.isEmpty()) {
                coordinates.append(';');
            }
            coordinates.append(point[1]).append(',').append(point[0]);
        }

        try {
            OsrmRouteResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/route/v1/driving/{coordinates}")
                            .queryParam("overview", "full")
                            .queryParam("geometries", "geojson")
                            .queryParam("steps", "false")
                            .build(coordinates.toString()))
                    .retrieve()
                    .body(OsrmRouteResponse.class);

            if (response == null || !"Ok".equals(response.code()) || response.routes() == null
                    || response.routes().isEmpty()) {
                return Optional.empty();
            }

            OsrmRoute osrmRoute = response.routes().getFirst();
            if (osrmRoute.geometry() == null || osrmRoute.geometry().coordinates() == null) {
                return Optional.empty();
            }

            // GeoJSON [lon, lat] -> arayüzün beklediği [lat, lon]
            List<double[]> geometry = osrmRoute.geometry().coordinates().stream()
                    .map(pair -> new double[] { pair.get(1), pair.get(0) })
                    .toList();

            List<Leg> legs = osrmRoute.legs() == null
                    ? List.of()
                    : osrmRoute.legs().stream()
                            .map(leg -> new Leg(leg.distance(), leg.duration()))
                            .toList();

            warned = false;
            return Optional.of(new Route(geometry, legs, osrmRoute.distance()));

        } catch (Exception exception) {
            if (!warned) {
                warned = true;
                log.warn("OSRM'e ulaşılamadı ({}), kuş uçuşu hesaba dönülüyor: {}",
                        baseUrl, exception.getMessage());
            }
            return Optional.empty();
        }
    }

    // --------------------------------------------------- OSRM yanıt modelleri

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmRouteResponse(String code, List<OsrmRoute> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmRoute(double distance, double duration, OsrmGeometry geometry, List<OsrmLeg> legs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmGeometry(String type, List<List<Double>> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmLeg(double distance, double duration) {
    }
}
