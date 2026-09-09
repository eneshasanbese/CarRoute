package com.eneshasanbese.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.repository.DriverRepository;
import com.eneshasanbese.repository.ServiceVehicleRepository;
import com.eneshasanbese.repository.WorkerRepository;
import com.eneshasanbese.util.GeoUtils;

/**
 * İşçileri servis araçlarına dağıtır. Seed verisinde her işçinin
 * {@code service_vehicle_id} alanı NULL; hangi işçinin hangi servise bineceğine
 * burası karar verir.
 *
 * <p>
 * Üç aşama var:
 * <ol>
 * <li><b>Kurulum</b> — pişmanlık (regret) sıralı açgözlü atama. Her işçi için en
 * yakın iki servis arasındaki fark hesaplanır; farkı büyük olan, yani yanlış
 * servise düşerse en çok kaybeden işçi önce yerleştirilir. Bu aşamada gerçek
 * kapasite yerine yumuşak bir tavan uygulanır, aksi halde şoförleri aynı ilçede
 * toplanmış servislerden biri herkesi toplayıp diğerini boş bırakıyor.</li>
 * <li><b>Asgari doluluk onarımı</b> — 5 kişinin altında kalan servise, fazlası
 * olan servislerden en uygun yolcu taşınır.</li>
 * <li><b>İyileştirme</b> — sınırlı yerel arama. Her işçi en yakın birkaç
 * alternatif servise taşınmayı dener; iki servisin toplam süresi azalıyorsa
 * taşınır. Kapasite tavanı ve asgari doluluk her adımda korunur.</li>
 * </ol>
 *
 * <p>
 * <b>Maliyet kaynağı iki türlü.</b> Yukarıdaki üç aşama kuş uçuşu mesafeyle
 * çalışır; 100 işçi × 10 servis × birkaç tur, yüz binlerce değerlendirme demek
 * ve buraya HTTP isteği koymak dağıtımı kullanılamaz hale getirirdi. Buna
 * karşılık {@link #assignOne} — yani sonradan eklenen tek bir personel —
 * OSRM'in yol matrisini kullanır: orada toplam 10 istek yeterli.
 *
 * <p>
 * Asgari doluluk yalnızca <em>dağıtım</em> aşamasında hedeflenir. Sonradan
 * personel silinip bir servis 5'in altına düşerse servisler birleştirilmez;
 * arayüz sadece uyarı rozeti gösterir.
 */
@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    /** Yerel aramada her işçi için denenecek alternatif servis sayısı. */
    private static final int NEIGHBOUR_CANDIDATES = 4;
    private static final int LOCAL_SEARCH_PASSES = 3;
    /** Bir taşımanın kabul edilmesi için kazanması gereken en az süre (dakika). */
    private static final double MIN_IMPROVEMENT_MINUTES = 0.01;
    /** Kurulumdaki yumuşak tavanın ortalama doluluğun ne kadar üstüne çıkabileceği. */
    private static final int SOFT_CAP_SLACK = 2;
    /** Onarım döngüsünün üst sınırı — takılı kalmaya karşı emniyet. */
    private static final int REPAIR_MAX_MOVES = 500;

    private final WorkerRepository workerRepository;
    private final DriverRepository driverRepository;
    private final ServiceVehicleRepository serviceVehicleRepository;
    private final RouteService routeService;
    private final RouteSettings settings;

    public AssignmentService(
            WorkerRepository workerRepository,
            DriverRepository driverRepository,
            ServiceVehicleRepository serviceVehicleRepository,
            RouteService routeService,
            RouteSettings settings) {
        this.workerRepository = workerRepository;
        this.driverRepository = driverRepository;
        this.serviceVehicleRepository = serviceVehicleRepository;
        this.routeService = routeService;
        this.settings = settings;
    }

    /** Servis id -> o servisin şoförü. */
    public Map<Long, Driver> driversByVehicleId() {
        Map<Long, Driver> drivers = new HashMap<>();
        for (Driver driver : driverRepository.findAll()) {
            ServiceVehicle vehicle = driver.getServiceVehicle();
            if (vehicle != null) {
                drivers.put(vehicle.getId(), driver);
            }
        }
        return drivers;
    }

    /**
     * Yalnızca servisi olmayan işçileri yerleştirir. Uygulama her açıldığında
     * çağrılır; hepsi zaten atanmışsa hiçbir şey yapmaz.
     */
    @Transactional
    public int assignUnassigned() {
        List<Worker> unassigned = workerRepository.findByServiceVehicleIsNull();
        if (unassigned.isEmpty()) {
            return 0;
        }

        log.info("Servisi olmayan {} işçi bulundu, dağıtım yapılıyor...", unassigned.size());
        distribute(unassigned, false);
        return unassigned.size();
    }

    /** Bütün işçilerin atamasını sıfırlayıp baştan dağıtır. */
    @Transactional
    public int reassignAll() {
        List<Worker> workers = workerRepository.findAll();
        workers.forEach(worker -> worker.setServiceVehicle(null));
        distribute(workers, true);
        return workers.size();
    }

    /**
     * Tek bir işçiyi, rotasını en az uzatan servise yerleştirir.
     *
     * <p>
     * Toplu dağıtımın aksine burada <b>gerçek yol maliyeti</b> kullanılır:
     * {@link RouteService#insertionCost} her servis için OSRM'in {@code /table}
     * servisinden bir yol matrisi alır. Tek kişilik ekleme kullanıcının
     * beklediği bir işlem olduğu için servis sayısı kadar (10) istek kabul
     * edilebilir; OSRM kapalıysa aynı hesap kuş uçuşu tahminle yapılır.
     *
     * @return işçinin atandığı servis
     * @throws IllegalStateException bütün servisler doluysa
     */
    @Transactional
    public ServiceVehicle assignOne(Worker worker) {
        List<ServiceVehicle> vehicles = serviceVehicleRepository.findAllByOrderByIdAsc();
        Map<Long, Driver> drivers = driversByVehicleId();
        Map<Long, List<Worker>> buckets = currentBuckets(vehicles, worker.getId());

        ServiceVehicle best = null;
        double bestDelta = Double.MAX_VALUE;

        for (ServiceVehicle vehicle : vehicles) {
            List<Worker> current = buckets.get(vehicle.getId());
            if (current.size() >= vehicle.getCapacity()) {
                continue;
            }

            Driver driver = drivers.get(vehicle.getId());
            // Servis başına tek /table isteği; adaysız ve adaylı turlar aynı yol
            // matrisi üzerinde kurulduğu için fark gerçek sapmayı ölçer.
            double delta = routeService.insertionCost(driver, current, worker).delta();

            if (delta < bestDelta) {
                bestDelta = delta;
                best = vehicle;
            }
        }

        if (best == null) {
            throw new IllegalStateException(
                    "Bütün servisler dolu; yeni personel atanamadı. Kapasiteyi artırın veya bir kişi çıkarın.");
        }

        worker.setServiceVehicle(best);
        workerRepository.save(worker);
        return best;
    }

    // ------------------------------------------------------------- dağıtım

    private void distribute(List<Worker> toPlace, boolean startEmpty) {
        List<ServiceVehicle> vehicles = serviceVehicleRepository.findAllByOrderByIdAsc();
        if (vehicles.isEmpty()) {
            log.warn("Hiç servis aracı yok, dağıtım atlandı.");
            return;
        }

        Map<Long, Driver> drivers = driversByVehicleId();
        Map<Long, List<Worker>> buckets = startEmpty
                ? emptyBuckets(vehicles)
                : currentBuckets(vehicles, null);

        int alreadyPlaced = buckets.values().stream().mapToInt(List::size).sum();
        int expectedTotal = alreadyPlaced + toPlace.size();

        greedyByRegret(toPlace, vehicles, drivers, buckets, softCap(expectedTotal, vehicles));
        repairMinimumOccupancy(vehicles, drivers, buckets);
        localSearch(vehicles, drivers, buckets);

        persist(vehicles, buckets);
    }

    /**
     * Kurulum aşamasının yumuşak tavanı: ortalama doluluğun biraz üstü. Gerçek
     * kapasiteyi (15) aşamaz; yerel arama sonradan bu tavanın üstüne çıkabilir.
     */
    private int softCap(int expectedTotal, List<ServiceVehicle> vehicles) {
        int average = (int) Math.ceil((double) expectedTotal / vehicles.size());
        return average + SOFT_CAP_SLACK;
    }

    private void persist(List<ServiceVehicle> vehicles, Map<Long, List<Worker>> buckets) {
        Map<Long, ServiceVehicle> byId = new HashMap<>();
        vehicles.forEach(vehicle -> byId.put(vehicle.getId(), vehicle));

        List<Worker> changed = new ArrayList<>();
        buckets.forEach((vehicleId, workers) -> {
            ServiceVehicle vehicle = byId.get(vehicleId);
            for (Worker worker : workers) {
                ServiceVehicle current = worker.getServiceVehicle();
                if (current == null || !vehicleId.equals(current.getId())) {
                    worker.setServiceVehicle(vehicle);
                    changed.add(worker);
                }
            }
        });

        workerRepository.saveAll(changed);
        log.info("Dağıtım tamamlandı. Servis doluluğu: {}",
                vehicles.stream()
                        .map(v -> "S" + v.getId() + "=" + buckets.get(v.getId()).size())
                        .toList());
    }

    /**
     * Yanlış servise düşerse en çok kaybedecek işçi önce yerleşir: en yakın servis
     * ile ikinci en yakın arasındaki fark ne kadar büyükse öncelik o kadar yüksek.
     */
    private void greedyByRegret(
            List<Worker> toPlace,
            List<ServiceVehicle> vehicles,
            Map<Long, Driver> drivers,
            Map<Long, List<Worker>> buckets,
            int softCap) {

        List<Worker> ordered = new ArrayList<>(toPlace);
        ordered.sort(Comparator.comparingDouble((Worker w) -> -regret(w, vehicles, drivers)));

        for (Worker worker : ordered) {
            ServiceVehicle best = pickNearest(worker, vehicles, drivers, buckets, softCap);
            if (best == null) {
                // Yumuşak tavan tıkandıysa gerçek kapasiteye kadar zorla.
                best = pickNearest(worker, vehicles, drivers, buckets, Integer.MAX_VALUE);
            }
            if (best == null) {
                log.warn("Kapasite kalmadı, {} numaralı işçi atanamadı.", worker.getId());
                continue;
            }
            buckets.get(best.getId()).add(worker);
        }
    }

    private ServiceVehicle pickNearest(
            Worker worker,
            List<ServiceVehicle> vehicles,
            Map<Long, Driver> drivers,
            Map<Long, List<Worker>> buckets,
            int cap) {

        ServiceVehicle best = null;
        double bestCost = Double.MAX_VALUE;

        for (ServiceVehicle vehicle : vehicles) {
            int size = buckets.get(vehicle.getId()).size();
            if (size >= Math.min(cap, vehicle.getCapacity())) {
                continue;
            }
            double cost = anchorDistanceKm(worker, drivers.get(vehicle.getId()));
            if (cost < bestCost) {
                bestCost = cost;
                best = vehicle;
            }
        }

        return best;
    }

    /**
     * Asgari doluluğun altında kalan servisleri doldurur: fazlası olan servislerden,
     * hedef servise en çok yaklaşan (kendi servisine göre en az kaybettiren) yolcu
     * taşınır.
     */
    private void repairMinimumOccupancy(
            List<ServiceVehicle> vehicles,
            Map<Long, Driver> drivers,
            Map<Long, List<Worker>> buckets) {

        int min = settings.getMinCapacity();
        int total = buckets.values().stream().mapToInt(List::size).sum();

        if (total < vehicles.size() * (long) min) {
            log.warn("Toplam {} kişi {} servisin asgari {} kişilik ihtiyacını karşılamıyor; "
                    + "bazı servisler eksik kalacak.", total, vehicles.size(), min);
        }

        for (int move = 0; move < REPAIR_MAX_MOVES; move++) {
            ServiceVehicle deficient = vehicles.stream()
                    .filter(v -> buckets.get(v.getId()).size() < min)
                    .min(Comparator.comparingInt(v -> buckets.get(v.getId()).size()))
                    .orElse(null);

            if (deficient == null) {
                return;
            }

            ServiceVehicle donor = null;
            int donorIndex = -1;
            double bestCost = Double.MAX_VALUE;

            for (ServiceVehicle candidate : vehicles) {
                if (candidate.getId().equals(deficient.getId())) {
                    continue;
                }
                List<Worker> pool = buckets.get(candidate.getId());
                if (pool.size() <= min) {
                    continue;
                }

                for (int i = 0; i < pool.size(); i++) {
                    Worker worker = pool.get(i);
                    double cost = anchorDistanceKm(worker, drivers.get(deficient.getId()))
                            - anchorDistanceKm(worker, drivers.get(candidate.getId()));
                    if (cost < bestCost) {
                        bestCost = cost;
                        donor = candidate;
                        donorIndex = i;
                    }
                }
            }

            if (donor == null) {
                log.warn("Servis-{} için verecek kimse kalmadı, {} kişide bırakıldı.",
                        deficient.getId(), buckets.get(deficient.getId()).size());
                return;
            }

            Worker moved = buckets.get(donor.getId()).remove(donorIndex);
            buckets.get(deficient.getId()).add(moved);
        }
    }

    private void localSearch(
            List<ServiceVehicle> vehicles,
            Map<Long, Driver> drivers,
            Map<Long, List<Worker>> buckets) {

        int min = settings.getMinCapacity();

        Map<Long, Integer> capacity = new HashMap<>();
        vehicles.forEach(v -> capacity.put(v.getId(), v.getCapacity()));

        Map<Long, Double> cost = new HashMap<>();
        vehicles.forEach(v -> cost.put(v.getId(),
                routeService.estimateMinutes(drivers.get(v.getId()), buckets.get(v.getId()))));

        for (int pass = 0; pass < LOCAL_SEARCH_PASSES; pass++) {
            boolean improved = false;

            for (ServiceVehicle from : vehicles) {
                List<Worker> source = buckets.get(from.getId());

                for (int index = 0; index < source.size(); index++) {
                    // Onarım aşamasında kurulan asgari doluluk bozulmasın.
                    if (source.size() <= min) {
                        break;
                    }

                    Worker worker = source.get(index);

                    List<Worker> withoutWorker = new ArrayList<>(source);
                    withoutWorker.remove(index);
                    double fromAfter = routeService.estimateMinutes(drivers.get(from.getId()), withoutWorker);
                    double fromGain = cost.get(from.getId()) - fromAfter;

                    ServiceVehicle bestTarget = null;
                    double bestTargetCost = 0;
                    // Yalnızca gerçek kazanç sağlayan taşımalar kabul edilir;
                    // eşitlikte taşımak turlar arası salınıma yol açardı.
                    double bestDelta = MIN_IMPROVEMENT_MINUTES;

                    for (ServiceVehicle to : nearestVehicles(worker, vehicles, drivers, from.getId())) {
                        List<Worker> target = buckets.get(to.getId());
                        if (target.size() >= capacity.get(to.getId())) {
                            continue;
                        }

                        List<Worker> withWorker = new ArrayList<>(target);
                        withWorker.add(worker);
                        double toAfter = routeService.estimateMinutes(drivers.get(to.getId()), withWorker);
                        double delta = fromGain - (toAfter - cost.get(to.getId()));

                        if (delta > bestDelta) {
                            bestDelta = delta;
                            bestTarget = to;
                            bestTargetCost = toAfter;
                        }
                    }

                    if (bestTarget != null) {
                        source.remove(index--);
                        buckets.get(bestTarget.getId()).add(worker);
                        cost.put(from.getId(), fromAfter);
                        cost.put(bestTarget.getId(), bestTargetCost);
                        improved = true;
                    }
                }
            }

            if (!improved) {
                break;
            }
        }
    }

    /** İşçiye şoför evi en yakın olan servisler — yerel aramada denenecek adaylar. */
    private List<ServiceVehicle> nearestVehicles(
            Worker worker,
            List<ServiceVehicle> vehicles,
            Map<Long, Driver> drivers,
            Long excludeVehicleId) {

        return vehicles.stream()
                .filter(v -> !v.getId().equals(excludeVehicleId))
                .sorted(Comparator.comparingDouble(v -> anchorDistanceKm(worker, drivers.get(v.getId()))))
                .limit(NEIGHBOUR_CANDIDATES)
                .toList();
    }

    private double regret(Worker worker, List<ServiceVehicle> vehicles, Map<Long, Driver> drivers) {
        double best = Double.MAX_VALUE;
        double second = Double.MAX_VALUE;

        for (ServiceVehicle vehicle : vehicles) {
            double distance = anchorDistanceKm(worker, drivers.get(vehicle.getId()));
            if (distance < best) {
                second = best;
                best = distance;
            } else if (distance < second) {
                second = distance;
            }
        }

        return second == Double.MAX_VALUE ? 0 : second - best;
    }

    private double anchorDistanceKm(Worker worker, Driver driver) {
        if (driver == null) {
            return Double.MAX_VALUE / 4;
        }
        return GeoUtils.haversineKm(
                worker.getLatitude(), worker.getLongitude(),
                driver.getLatitude(), driver.getLongitude());
    }

    private Map<Long, List<Worker>> emptyBuckets(List<ServiceVehicle> vehicles) {
        Map<Long, List<Worker>> buckets = new HashMap<>();
        vehicles.forEach(vehicle -> buckets.put(vehicle.getId(), new ArrayList<>()));
        return buckets;
    }

    /**
     * Servislerin mevcut yolcu listeleri. {@code excludeWorkerId} verilirse o işçi
     * listelerden çıkarılır — güncellenen bir kişiyi kendi eski servisiyle
     * kıyaslamamak için.
     */
    private Map<Long, List<Worker>> currentBuckets(List<ServiceVehicle> vehicles, Long excludeWorkerId) {
        Map<Long, List<Worker>> buckets = emptyBuckets(vehicles);

        for (Worker worker : workerRepository.findAll()) {
            ServiceVehicle vehicle = worker.getServiceVehicle();
            if (vehicle == null) {
                continue;
            }
            if (excludeWorkerId != null && excludeWorkerId.equals(worker.getId())) {
                continue;
            }
            List<Worker> bucket = buckets.get(vehicle.getId());
            if (bucket != null) {
                bucket.add(worker);
            }
        }

        return buckets;
    }
}
