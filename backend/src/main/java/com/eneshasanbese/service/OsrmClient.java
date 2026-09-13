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
 * OSRM (Open Source Routing Machine) istemcisi. İki ayrı servisini kullanıyoruz
 * ve ikisi farklı soruya cevap veriyor:
 *
 * <ul>
 * <li>{@link #matrix} — {@code /table}: bir nokta kümesindeki
 * <b>bütün ikililerin</b> yol mesafesi ve boş yol süresi. Durak sırasına karar veren algoritmanın
 * ihtiyacı budur: "5. kişiyle 9. kişinin yerini değiştirsem ne olur" sorusu,
 * henüz denenmemiş bacakların maliyetini bilmeyi gerektirir. Aynı bilgiyi
 * {@code /route} ile toplamak N² ayrı istek ederdi; {@code /table} tek istekte
 * N×N mesafe döndürür.</li>
 * <li>{@link #route} — {@code /route}: sırası <b>zaten belli</b> noktaları
 * gerçek yollardan bağlayan çizgi ve o sıranın bacak mesafeleri. Haritaya
 * çizilen şey budur.</li>
 * </ul>
 *
 * <p>
 * İkisi de ulaşılamaz olabilir. O durumda metotlar boş döner ve çağıran taraf
 * kuş uçuşu × karayolu çarpanı tahminine geri düşer — uygulama OSRM olmadan da
 * çalışır, sadece sıralama körleşir ve harita düz çizgiye iner.
 */
@Service
public class OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);

    private final boolean enabled;
    private final boolean tableEnabled;
    private final String baseUrl;
    private final RestClient restClient;
    /** Arka arkaya hata alındığında logu doldurmamak için. */
    private volatile boolean warned;

    public OsrmClient(
            @Value("${carroute.osrm.enabled:true}") boolean enabled,
            @Value("${carroute.osrm.table-enabled:true}") boolean tableEnabled,
            @Value("${carroute.osrm.base-url:http://localhost:5000}") String baseUrl,
            @Value("${carroute.osrm.timeout-ms:8000}") int timeoutMs) {
        this.enabled = enabled;
        this.tableEnabled = tableEnabled;
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

    public boolean isTableEnabled() {
        return enabled && tableEnabled;
    }

    // --------------------------------------------------------------- /table

    /**
     * Bir nokta kümesinin yol mesafeleri ve boş yol süreleri.
     *
     * @param meters  N×N mesafe (metre)
     * @param seconds N×N süre (saniye), <b>trafiksiz</b>; OSRM süreyi döndürmediyse
     *                null
     */
    public record Matrix(double[][] meters, double[][] seconds) {
    }

    /**
     * Nokta kümesindeki bütün ikililerin yol mesafesi ve boş yol süresi.
     *
     * <p>
     * <b>Süre neden de isteniyor:</b> bir bacağın gerçek süresi, o bacağın boş
     * yol süresinin bölgesel tıkanıklık çarpanıyla ölçeklenmesiyle bulunuyor.
     * Mesafeyi bölgesel ortalama hıza bölmek aynı sonucu vermez — sokakla
     * çevre yolu aynı hücrede olduğunda mesafe, aracın hangisinden geçtiğini
     * bilmez; OSRM'in süresi bilir.
     *
     * <p>
     * <b>Matris simetrik değildir</b> — tek yön, bölünmüş yol ve köprü çıkışları
     * yüzünden A→B ile B→A farklı çıkabilir. Bunu kullanan sıralama kodunun
     * simetri varsayan kısayollar kullanmaması gerekir.
     *
     * <p>
     * Ulaşılamayan çiftler için OSRM {@code null} döndürür; burada {@code NaN}'a
     * çevriliyor ve çağıran taraf yalnızca o bacak için kuş uçuşu tahmine
     * düşüyor.
     *
     * <p>
     * OSRM'in {@code --max-table-size} sınırı nokta sayısını sınırlar
     * (docker-compose'da 2000). En büyük çağrı bütün sistemi kapsayan ofis +
     * şoförler + işçiler matrisi; bu veri setinde 114 nokta.
     *
     * @param points [lat, lon] noktaları (en az 2 tane)
     * @return N×N mesafe ve süre; servis kapalı, ulaşılamaz ya da yanıt beklenen
     *         boyutta değilse boş
     */
    public Optional<Matrix> matrix(List<double[]> points) {
        if (!isTableEnabled() || points.size() < 2) {
            return Optional.empty();
        }

        int size = points.size();

        try {
            OsrmTableResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/table/v1/driving/{coordinates}")
                            .queryParam("annotations", "distance,duration")
                            .build(coordinates(points)))
                    .retrieve()
                    .body(OsrmTableResponse.class);

            if (response == null || !"Ok".equals(response.code())) {
                return Optional.empty();
            }

            double[][] meters = square(response.distances(), size);
            if (meters == null) {
                return Optional.empty();
            }

            warned = false;
            // Süre olmadan da çalışılır: çağıran taraf mesafeyi bölgesel hıza böler.
            return Optional.of(new Matrix(meters, square(response.durations(), size)));

        } catch (Exception exception) {
            warn("/table", exception);
            return Optional.empty();
        }
    }

    /**
     * OSRM'in satır listesini kare diziye çevirir. Beklenen boyutta değilse null;
     * tek tek ulaşılamayan çiftler {@code null} geldiği için {@link Double#NaN}
     * olur ve çağıran taraf yalnızca o bacak için tahmine düşer.
     */
    private static double[][] square(List<List<Double>> rows, int size) {
        if (rows == null || rows.size() != size) {
            return null;
        }

        double[][] matrix = new double[size][size];
        for (int row = 0; row < size; row++) {
            List<Double> values = rows.get(row);
            if (values == null || values.size() != size) {
                return null;
            }
            for (int column = 0; column < size; column++) {
                Double value = values.get(column);
                matrix[row][column] = value == null ? Double.NaN : value;
            }
        }

        return matrix;
    }

    // --------------------------------------------------------------- /route

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

        try {
            OsrmRouteResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/route/v1/driving/{coordinates}")
                            .queryParam("overview", "full")
                            .queryParam("geometries", "geojson")
                            .queryParam("steps", "false")
                            .build(coordinates(points)))
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
            warn("/route", exception);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------- yardımcı

    /** OSRM koordinatları {@code lon,lat} sırasıyla ve ";" ile ayrılmış bekler. */
    private static String coordinates(List<double[]> points) {
        StringBuilder builder = new StringBuilder();
        for (double[] point : points) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(point[1]).append(',').append(point[0]);
        }
        return builder.toString();
    }

    private void warn(String endpoint, Exception exception) {
        if (!warned) {
            warned = true;
            log.warn("OSRM {} çağrısı başarısız ({}), kuş uçuşu hesaba dönülüyor: {}",
                    endpoint, baseUrl, exception.getMessage());
        }
    }

    // --------------------------------------------------- OSRM yanıt modelleri

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OsrmTableResponse(String code, List<List<Double>> distances, List<List<Double>> durations) {
    }

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
