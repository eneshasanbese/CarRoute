package com.eneshasanbese.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eneshasanbese.config.RouteSettings;
import com.eneshasanbese.entity.Driver;
import com.eneshasanbese.entity.ServiceVehicle;
import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.enums.Shift;
import com.eneshasanbese.repository.DriverRepository;
import com.eneshasanbese.repository.ServiceVehicleRepository;
import com.eneshasanbese.repository.WorkerRepository;
import com.eneshasanbese.service.RouteService.LoadEstimate;
import com.eneshasanbese.util.GeoUtils;
import com.eneshasanbese.util.RoadMatrix;

/**
 * İşçileri servis araçlarına dağıtır. Seed verisinde her işçinin
 * {@code service_vehicle_id} alanı NULL; hangi işçinin hangi servise bineceğine
 * burası karar verir.
 *
 * <p>
 * <b>Ölçü birimi "yük".</b> Bir servisin yükü, iki seferin (sabah + akşam)
 * toplam süresine kural ihlali cezasının eklenmesiyle bulunur
 * ({@link Plan#loadOf}). Üç şeyi aynı anda ölçtüğü için önemli:
 * <ul>
 * <li>Akşam da hesaba giriyor. Aynı kişiler akşam da aynı araca biniyor ve
 * ölçüme göre ihlallerin çoğu akşamda; yalnızca sabaha bakan bir atama, akşamı
 * kör bir noktada bırakırdı.</li>
 * <li>Kural görünür. 90 dakikayı aşan her dakika yapılandırılmış bir katsayıyla
 * cezalandırılır, böylece arama "toplam süreyi biraz uzatsam da şu
 * kişiyi dolu servisten çıkarsam" diyebiliyor.</li>
 * <li>Mesafe gerçek. Bütün hesap {@link RoadMatrix} üzerinden yürüyor.</li>
 * </ul>
 *
 * <p>
 * <b>Neden tek matris.</b> Dağıtım on binlerce kez "bu kişiyi buraya koysam ne
 * olur" diye soruyor; her soru için OSRM'e gitmek dakikalar sürerdi. Bu yüzden
 * dağıtım bugüne kadar kuş uçuşu mesafeyle çalışıyordu ve Boğaz'ın iki
 * yakasındaki iki evi 2 km sanıyordu. Artık başta <b>bir</b> {@code /table}
 * isteğiyle bütün noktaların yol mesafesi alınıyor, sonrası dizi okuması.
 *
 * <p>
 * Dört aşama var:
 * <ol>
 * <li><b>Kurulum</b> — pişmanlık (regret) sıralı açgözlü atama. Her işçi için en
 * ucuz iki servis arasındaki fark hesaplanır; farkı büyük olan, yani yanlış
 * servise düşerse en çok kaybeden işçi önce yerleştirilir. Bu aşamada gerçek
 * kapasite yerine yumuşak bir tavan uygulanır, aksi halde şoförleri aynı ilçede
 * toplanmış servislerden biri herkesi toplayıp diğerini boş bırakıyor.</li>
 * <li><b>Asgari doluluk onarımı</b> — 5 kişinin altında kalan servise, fazlası
 * olan servislerden yükü en az artıran yolcu taşınır.</li>
 * <li><b>Taşıma</b> — her işçi en yakın birkaç alternatif servise taşınmayı
 * dener; iki servisin toplam yükü azalıyorsa taşınır.</li>
 * <li><b>Takas</b> — iki servisten birer kişi yer değiştirir. Taşımanın tek
 * başına çözemediği durum bu: karşılıklı yanlış servisteki iki kişide her tek
 * yönlü hamle ya kapasiteyi ya asgari doluluğu bozduğu için reddediliyor ve
 * ikili sonsuza kadar yanlış yerde kalıyordu. Takas kişi sayılarını
 * değiştirmediği için iki kısıt da kendiliğinden korunur.</li>
 * </ol>
 *
 * <p>
 * Asgari doluluk yalnızca <em>dağıtım</em> aşamasında hedeflenir. Sonradan
 * personel silinip bir servis 5'in altına düşerse servisler birleştirilmez;
 * arayüz sadece uyarı rozeti gösterir.
 */
@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    /** Taşımada her işçi için denenecek alternatif servis sayısı. */
    private static final int NEIGHBOUR_CANDIDATES = 4;
    /** Takasta denenecek hedef servis sayısı — taşımadan dar, çünkü çift kuruyor. */
    private static final int SWAP_TARGET_VEHICLES = 2;
    /** Hedef serviste denenecek takas adayı sayısı. */
    private static final int SWAP_CANDIDATES = 3;
    private static final int LOCAL_SEARCH_PASSES = 3;
    /** Bir hamlenin kabul edilmesi için kazanması gereken en az yük. */
    private static final double MIN_IMPROVEMENT = 0.01;
    /** Kurulumdaki yumuşak tavanın ortalama doluluğun ne kadar üstüne çıkabileceği. */
    private static final int SOFT_CAP_SLACK = 2;
    /** Onarım döngüsünün üst sınırı — takılı kalmaya karşı emniyet. */
    private static final int REPAIR_MAX_MOVES = 500;

    private final WorkerRepository workerRepository;
    private final DriverRepository driverRepository;
    private final ServiceVehicleRepository serviceVehicleRepository;
    private final RouteService routeService;
    private final RouteSettings settings;
    private final AssignmentLock assignmentLock;

    public AssignmentService(
            WorkerRepository workerRepository,
            DriverRepository driverRepository,
            ServiceVehicleRepository serviceVehicleRepository,
            RouteService routeService,
            RouteSettings settings,
            AssignmentLock assignmentLock) {
        this.workerRepository = workerRepository;
        this.driverRepository = driverRepository;
        this.serviceVehicleRepository = serviceVehicleRepository;
        this.routeService = routeService;
        this.settings = settings;
        this.assignmentLock = assignmentLock;
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
     * Yalnızca servisi olmayan işçileri yerleştirir. Uygulama her açıldığında ve
     * bir şoför silinip servisinin yolcuları boşa çıktığında çağrılır; hepsi
     * zaten atanmışsa hiçbir şey yapmaz.
     */
    @Transactional
    public int assignUnassigned() {
        assignmentLock.acquire();
        List<Worker> unassigned = workerRepository.findByServiceVehicleIsNullOrderByIdAsc();
        if (unassigned.isEmpty()) {
            return 0;
        }

        log.info("Servisi olmayan {} işçi bulundu, dağıtım yapılıyor...", unassigned.size());
        distribute(unassigned, false);
        return unassigned.size();
    }

    /**
     * Mevcut atamayı koruyarak dengeler: kimse sıfırlanmaz, yalnızca asgari
     * doluluk onarımı ve yerel arama çalışır.
     *
     * <p>
     * Yeni bir servis eklendiğinde ve bir şoförün ev adresi değiştiğinde
     * çağrılıyor. {@link #reassignAll} yerine bunun
     * tercih edilmesi bilinçli: baştan dağıtım muhtemelen biraz daha iyi bir
     * sonuç bulur ama neredeyse herkesin servisini değiştirir. Gerçek bir kurumda
     * personelin servisi durduk yere değişmemeli; burada yalnızca taşınması
     * gerçekten kazandıran kişiler taşınıyor.
     *
     * @return dengelemeden sonra servislere dağılmış toplam kişi sayısı
     */
    @Transactional
    public int rebalance() {
        assignmentLock.acquire();
        distribute(List.of(), false);
        return workerRepository.findAllByOrderByIdAsc().size();
    }

    /**
     * Bütün işçilerin atamasını sıfırlayıp baştan dağıtır.
     *
     * <p>
     * {@link #rebalance} mevcut dağılımı çıpa alır ve yalnızca kazandıran
     * hamleleri yapar; burada çıpa yok. Kurulum aşaması boş kovalarla yeniden
     * koşuyor, dolayısıyla arama bugünkü dağılımın etrafındaki yerel çukurdan
     * çıkabiliyor. Bedeli, herkesin servisinin değişebilmesi — bu yüzden
     * yalnızca kullanıcı açıkça istediğinde çağrılır.
     *
     * @return toplam kişi ve bunların kaçının servisinin değiştiği
     */
    @Transactional
    public ReassignSummary reassignAll() {
        assignmentLock.acquire();
        List<Worker> workers = workerRepository.findAllByOrderByIdAsc();
        List<ServiceVehicle> vehicles = serviceVehicleRepository.findAllByOrderByIdAsc();

        // Kaç kişinin taşındığını söyleyebilmek için önceki dağılımı saklıyoruz;
        // kullanıcı için anlamlı olan sayı bu, toplam personel değil.
        Map<Long, Long> before = new HashMap<>();
        workers.forEach(worker -> before.put(worker.getId(), vehicleIdOf(worker)));

        double previousLoad = comparableLoadOf(workers, vehicles);

        workers.forEach(worker -> worker.setServiceVehicle(null));
        double freshLoad = distribute(workers, true);

        if (Double.isFinite(previousLoad) && Double.isFinite(freshLoad) && freshLoad >= previousLoad) {
            restore(workers, before, vehicles);
            log.info("Baştan dağıtım mevcut tabloyu iyileştirmedi ({} >= {}); eski atama geri alındı.",
                    Math.round(freshLoad), Math.round(previousLoad));
            return new ReassignSummary(workers.size(), 0);
        }

        int moved = 0;
        for (Worker worker : workers) {
            if (!Objects.equals(before.get(worker.getId()), vehicleIdOf(worker))) {
                moved++;
            }
        }

        log.info("Yeniden dağıtım: {} kişiden {} kişinin servisi değişti (yük {} -> {}).",
                workers.size(), moved, Math.round(previousLoad), Math.round(freshLoad));
        return new ReassignSummary(workers.size(), moved);
    }

    /**
     * Mevcut dağılımın toplam yükü — baştan dağıtımın sonucuyla kıyaslamak için.
     *
     * <p>
     * Sıfırdan kurmak <b>her zaman</b> daha iyi sonuç vermiyor: bugünkü tablo da
     * aynı yerel aramadan geçmiş durumda ve açgözlü kurulum bazen daha kötü bir
     * havzaya düşüyor. Ölçüldü: 104 kişilik gerçek veride baştan dağıtım 62
     * kişiyi taşıyıp toplam yolu 32 km uzatmıştı. Bu yüzden yeni tablo yalnızca
     * gerçekten kazandırıyorsa kabul ediliyor; düğme tabloyu bozamaz.
     *
     * <p>
     * Kıyas ancak herkes bir servisteyse anlamlı. Atanmamış kişi varsa
     * {@code NaN} döner ve yeni dağıtım koşulsuz kabul edilir — yoksa daha az
     * kişiyi taşıdığı için ucuz görünen eski tabloya dönülür ve o kişiler
     * servissiz kalırdı.
     */
    private double comparableLoadOf(List<Worker> workers, List<ServiceVehicle> vehicles) {
        if (vehicles.isEmpty() || workers.stream().anyMatch(worker -> worker.getServiceVehicle() == null)) {
            return Double.NaN;
        }

        Map<Long, Driver> drivers = driversByVehicleId();
        Map<Long, List<Worker>> buckets = currentBuckets(vehicles, null);
        Plan plan = new Plan(vehicles, drivers, buckets, matrixFor(drivers, workers));
        return plan.totalLoad();
    }

    /** İşçileri verilen servis dağılımına geri döndürür. */
    private void restore(List<Worker> workers, Map<Long, Long> assignment, List<ServiceVehicle> vehicles) {
        Map<Long, ServiceVehicle> byId = new HashMap<>();
        vehicles.forEach(vehicle -> byId.put(vehicle.getId(), vehicle));

        for (Worker worker : workers) {
            Long vehicleId = assignment.get(worker.getId());
            worker.setServiceVehicle(vehicleId == null ? null : byId.get(vehicleId));
        }

        workerRepository.saveAll(workers);
    }

    private static Long vehicleIdOf(Worker worker) {
        ServiceVehicle vehicle = worker.getServiceVehicle();
        return vehicle == null ? null : vehicle.getId();
    }

    /**
     * Yeniden dağıtımın sonucu.
     *
     * @param total kayıtlı toplam personel
     * @param moved bunların kaçı başka bir servise geçti
     */
    public record ReassignSummary(int total, int moved) {
    }

    /**
     * Tek bir işçiyi, iki seferin toplam yükünü en az artıran servise
     * yerleştirir.
     *
     * <p>
     * Toplu dağıtımla <b>aynı ölçüyü</b> kullanır. Bu bilerek: iki yerde iki
     * farklı maliyet tanımı olsaydı, tek tek eklenen personelin gittiği servis
     * ile "yeniden dağıt" dendiğinde gideceği servis birbirini tutmazdı. Kural
     * cezası burada da işlediği için dolu bir servis, sırf o mahalleden geçiyor
     * diye yeni yolcu almaz.
     *
     * @return işçinin atandığı servis
     * @throws IllegalStateException hiç servis yoksa ya da bütün servisler doluysa
     */
    @Transactional
    public ServiceVehicle assignOne(Worker worker) {
        assignmentLock.acquire();
        List<ServiceVehicle> vehicles = serviceVehicleRepository.findAllByOrderByIdAsc();
        if (vehicles.isEmpty()) {
            throw new IllegalStateException(
                    "Henüz hiç servis yok; personel eklemeden önce bir şoför ve servisini ekleyin.");
        }
        Map<Long, Driver> drivers = driversByVehicleId();
        Map<Long, List<Worker>> buckets = currentBuckets(vehicles, worker.getId());

        Plan plan = new Plan(vehicles, drivers, buckets, matrixFor(drivers, allWorkersWith(worker)));

        ServiceVehicle best = null;
        double bestDelta = Double.MAX_VALUE;

        for (ServiceVehicle vehicle : vehicles) {
            if (buckets.get(vehicle.getId()).size() >= vehicle.getCapacity()) {
                continue;
            }

            double delta = plan.marginalLoad(vehicle.getId(), worker);
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

    /** @return dağıtım sonrası toplam yük; servis yoksa {@code NaN} */
    private double distribute(List<Worker> toPlace, boolean startEmpty) {
        List<ServiceVehicle> vehicles = serviceVehicleRepository.findAllByOrderByIdAsc();
        if (vehicles.isEmpty()) {
            log.warn("Hiç servis aracı yok, dağıtım atlandı.");
            return Double.NaN;
        }

        Map<Long, Driver> drivers = driversByVehicleId();
        Map<Long, List<Worker>> buckets = startEmpty
                ? emptyBuckets(vehicles)
                : currentBuckets(vehicles, null);

        List<Worker> everyone = new ArrayList<>(toPlace);
        buckets.values().forEach(everyone::addAll);

        Plan plan = new Plan(vehicles, drivers, buckets, matrixFor(drivers, everyone));

        int expectedTotal = everyone.size();
        long started = System.currentTimeMillis();

        greedyByRegret(plan, toPlace, softCap(expectedTotal, vehicles));
        repairMinimumOccupancy(plan);
        localSearch(plan);

        persist(vehicles, buckets);
        plan.logRuleStatus(System.currentTimeMillis() - started);
        return plan.totalLoad();
    }

    private RoadMatrix matrixFor(Map<Long, Driver> drivers, List<Worker> workers) {
        return routeService.buildRoadMatrix(drivers.values(), workers).orElse(null);
    }

    /** Kayıtlı bütün işçiler, verilen kişi henüz listede yoksa o da dahil. */
    private List<Worker> allWorkersWith(Worker worker) {
        List<Worker> workers = new ArrayList<>(workerRepository.findAllByOrderByIdAsc());
        boolean present = workers.stream()
                .anyMatch(known -> known.getId() != null && known.getId().equals(worker.getId()));
        if (!present) {
            workers.add(worker);
        }
        return workers;
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
     * Yanlış servise düşerse en çok kaybedecek işçi önce yerleşir: en ucuz servis
     * ile ikinci en ucuz arasındaki fark ne kadar büyükse öncelik o kadar
     * yüksek.
     *
     * <p>
     * Sıralama ucuz sapma ölçüsüyle yapılır ({@link Plan#detourKm}), yerleştirme
     * kararı ise gerçek yükle. Sıralamaya tam ölçüyü koymak, her yerleştirmeden
     * sonra kalan herkesin pişmanlığını yeniden hesaplamak demek olurdu.
     */
    private void greedyByRegret(Plan plan, List<Worker> toPlace, int softCap) {
        List<Worker> ordered = new ArrayList<>(toPlace);
        ordered.sort(Comparator.comparingDouble((Worker w) -> -plan.regret(w)));

        for (Worker worker : ordered) {
            ServiceVehicle best = plan.cheapestFor(worker, softCap);
            if (best == null) {
                // Yumuşak tavan tıkandıysa gerçek kapasiteye kadar zorla.
                best = plan.cheapestFor(worker, Integer.MAX_VALUE);
            }
            if (best == null) {
                log.warn("Kapasite kalmadı, {} numaralı işçi atanamadı.", worker.getId());
                continue;
            }
            plan.place(best.getId(), worker);
        }
    }

    /**
     * Asgari doluluğun altında kalan servisleri doldurur: fazlası olan
     * servislerden, iki servisin toplam yükünü en az artıran yolcu taşınır.
     */
    private void repairMinimumOccupancy(Plan plan) {
        int min = settings.getMinCapacity();
        int total = plan.buckets.values().stream().mapToInt(List::size).sum();

        if (total < plan.vehicles.size() * (long) min) {
            log.warn("Toplam {} kişi {} servisin asgari {} kişilik ihtiyacını karşılamıyor; "
                    + "bazı servisler eksik kalacak.", total, plan.vehicles.size(), min);
        }

        for (int move = 0; move < REPAIR_MAX_MOVES; move++) {
            ServiceVehicle deficient = plan.vehicles.stream()
                    .filter(v -> plan.membersOf(v.getId()).size() < min)
                    .min(Comparator.comparingInt(v -> plan.membersOf(v.getId()).size()))
                    .orElse(null);

            if (deficient == null) {
                return;
            }

            ServiceVehicle donor = null;
            int donorIndex = -1;
            double bestCost = Double.MAX_VALUE;

            for (ServiceVehicle candidate : plan.vehicles) {
                if (candidate.getId().equals(deficient.getId())) {
                    continue;
                }
                List<Worker> pool = plan.membersOf(candidate.getId());
                if (pool.size() <= min) {
                    continue;
                }

                for (int i = 0; i < pool.size(); i++) {
                    Worker worker = pool.get(i);
                    double cost = plan.marginalLoad(deficient.getId(), worker)
                            - plan.releaseGain(candidate.getId(), i);
                    if (cost < bestCost) {
                        bestCost = cost;
                        donor = candidate;
                        donorIndex = i;
                    }
                }
            }

            if (donor == null) {
                log.warn("Servis-{} için verecek kimse kalmadı, {} kişide bırakıldı.",
                        deficient.getId(), plan.membersOf(deficient.getId()).size());
                return;
            }

            plan.transfer(donor.getId(), donorIndex, deficient.getId());
        }
    }

    // -------------------------------------------------------- yerel arama

    private void localSearch(Plan plan) {
        for (int pass = 0; pass < LOCAL_SEARCH_PASSES; pass++) {
            boolean improved = relocatePass(plan);
            improved |= swapPass(plan);

            if (!improved) {
                return;
            }
        }
    }

    /** Tek kişiyi başka servise taşır. Doluluk değiştiği için iki kısıt da denetlenir. */
    private boolean relocatePass(Plan plan) {
        int min = settings.getMinCapacity();
        boolean improved = false;

        for (ServiceVehicle from : plan.vehicles) {
            List<Worker> source = plan.membersOf(from.getId());

            for (int index = 0; index < source.size(); index++) {
                // Onarım aşamasında kurulan asgari doluluk bozulmasın.
                if (source.size() <= min) {
                    break;
                }

                Worker worker = source.get(index);
                double fromAfter = plan.loadWithout(from.getId(), index);
                double fromGain = plan.load(from.getId()) - fromAfter;

                ServiceVehicle bestTarget = null;
                double bestTargetLoad = 0;
                // Yalnızca gerçek kazanç sağlayan taşımalar kabul edilir;
                // eşitlikte taşımak servisler arası salınıma yol açardı.
                double bestGain = MIN_IMPROVEMENT;

                for (ServiceVehicle to : plan.nearestVehicles(worker, from.getId(), NEIGHBOUR_CANDIDATES)) {
                    if (plan.membersOf(to.getId()).size() >= to.getCapacity()) {
                        continue;
                    }

                    double toAfter = plan.loadWith(to.getId(), worker);
                    double gain = fromGain - (toAfter - plan.load(to.getId()));

                    if (gain > bestGain) {
                        bestGain = gain;
                        bestTarget = to;
                        bestTargetLoad = toAfter;
                    }
                }

                if (bestTarget != null) {
                    plan.applyRelocate(from.getId(), index--, bestTarget.getId(), fromAfter, bestTargetLoad);
                    improved = true;
                }
            }
        }

        return improved;
    }

    /**
     * İki servisten birer kişiyi yer değiştirir.
     *
     * <p>
     * Aday havuzu bilerek dar: her işçi için en yakın {@link #SWAP_TARGET_VEHICLES}
     * servis, her serviste de o servise değil <em>bu</em> servise daha yakın
     * duran {@link #SWAP_CANDIDATES} kişi denenir. Bütün çiftleri denemek
     * 10 × 15 × 9 × 15 değerlendirme demek olurdu; ön eleme, kazanma ihtimali
     * olmayan çiftleri hiç ölçmeden atıyor.
     */
    private boolean swapPass(Plan plan) {
        boolean improved = false;

        for (ServiceVehicle from : plan.vehicles) {
            List<Worker> source = plan.membersOf(from.getId());

            for (int index = 0; index < source.size(); index++) {
                Worker outgoing = source.get(index);

                ServiceVehicle bestVehicle = null;
                int bestIncomingIndex = -1;
                double bestGain = MIN_IMPROVEMENT;
                double bestFromLoad = 0;
                double bestToLoad = 0;

                for (ServiceVehicle to : plan.nearestVehicles(outgoing, from.getId(), SWAP_TARGET_VEHICLES)) {
                    for (int incomingIndex : plan.swapCandidates(to.getId(), from.getId(), outgoing)) {
                        Worker incoming = plan.membersOf(to.getId()).get(incomingIndex);

                        double fromAfter = plan.loadSwapped(from.getId(), index, incoming);
                        double toAfter = plan.loadSwapped(to.getId(), incomingIndex, outgoing);
                        double gain = plan.load(from.getId()) + plan.load(to.getId()) - fromAfter - toAfter;

                        if (gain > bestGain) {
                            bestGain = gain;
                            bestVehicle = to;
                            bestIncomingIndex = incomingIndex;
                            bestFromLoad = fromAfter;
                            bestToLoad = toAfter;
                        }
                    }
                }

                if (bestVehicle != null) {
                    plan.applySwap(from.getId(), index, bestVehicle.getId(), bestIncomingIndex,
                            bestFromLoad, bestToLoad);
                    improved = true;
                }
            }
        }

        return improved;
    }

    // ----------------------------------------------------------- kovalar

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

        for (Worker worker : workerRepository.findAllByOrderByIdAsc()) {
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

    // ------------------------------------------------------------- durum

    /**
     * Dağıtımın çalışma durumu: kim hangi serviste, her servis ne kadar yük
     * tutuyor ve bu yükler hangi yol matrisi üzerinden ölçülüyor.
     *
     * <p>
     * Yükler önbelleklenir; yalnızca kova değiştiğinde güncellenir. Arama
     * {@code load()} değerini her karşılaştırmada okuduğu için bunu her seferinde
     * yeniden hesaplamak aramanın kendisinden pahalıya geliyordu.
     */
    private final class Plan {

        private final List<ServiceVehicle> vehicles;
        private final Map<Long, Driver> drivers;
        private final Map<Long, List<Worker>> buckets;
        private final RoadMatrix matrix;
        private final Map<Long, Double> loads = new HashMap<>();
        private final int rideLimit = settings.getMaxRideMinutes();
        private final double penaltyWeight = settings.getRulePenaltyWeight();

        Plan(List<ServiceVehicle> vehicles, Map<Long, Driver> drivers,
                Map<Long, List<Worker>> buckets, RoadMatrix matrix) {
            this.vehicles = vehicles;
            this.drivers = drivers;
            this.buckets = buckets;
            this.matrix = matrix;
        }

        List<Worker> membersOf(Long vehicleId) {
            return buckets.get(vehicleId);
        }

        /** Servisin şu anki yükü — önbellekten. */
        double load(Long vehicleId) {
            return loads.computeIfAbsent(vehicleId, id -> loadOf(id, membersOf(id)));
        }

        /**
         * Bütün servislerin yükü. Aramanın küçültmeye çalıştığı sayı bu; iki
         * dağıtımı kıyaslarken de bakılması gereken yer burası.
         */
        double totalLoad() {
            return vehicles.stream().mapToDouble(vehicle -> load(vehicle.getId())).sum();
        }

        /**
         * Bir yolcu kümesinin yükü: iki seferin toplam süresi + kural cezası.
         *
         * <p>
         * Sabah ve akşam ayrı ayrı ölçülür çünkü akşam turu sabahın tersi değil;
         * matris asimetrik, tıkanıklık her koridorda aynı oranda artmıyor ve
         * kuralı zorlayan kişi sabah ilk binen, akşam en son inen.
         */
        double loadOf(Long vehicleId, List<Worker> members) {
            Driver driver = drivers.get(vehicleId);
            double score = 0;

            for (Shift shift : Shift.values()) {
                LoadEstimate estimate = routeService.estimateLoad(driver, members, matrix, shift);
                score += estimate.totalMinutes()
                        + penaltyWeight * Math.max(0, estimate.maxRideMinutes() - rideLimit);
            }

            return score;
        }

        /** Bir kişi eklenirse servisin yükü ne olur. */
        double loadWith(Long vehicleId, Worker worker) {
            List<Worker> members = new ArrayList<>(membersOf(vehicleId));
            members.add(worker);
            return loadOf(vehicleId, members);
        }

        /** Bir kişi çıkarılırsa servisin yükü ne olur. */
        double loadWithout(Long vehicleId, int index) {
            List<Worker> members = new ArrayList<>(membersOf(vehicleId));
            members.remove(index);
            return loadOf(vehicleId, members);
        }

        /** {@code index}. kişi yerine {@code incoming} binerse servisin yükü ne olur. */
        double loadSwapped(Long vehicleId, int index, Worker incoming) {
            List<Worker> members = new ArrayList<>(membersOf(vehicleId));
            members.set(index, incoming);
            return loadOf(vehicleId, members);
        }

        /** Bir kişiyi eklemenin servise getirdiği ek yük. */
        double marginalLoad(Long vehicleId, Worker worker) {
            return loadWith(vehicleId, worker) - load(vehicleId);
        }

        /** Bir kişiyi çıkarmanın servise kazandırdığı yük. */
        double releaseGain(Long vehicleId, int index) {
            return load(vehicleId) - loadWithout(vehicleId, index);
        }

        // ------------------------------------------------------ hamleler

        void place(Long vehicleId, Worker worker) {
            membersOf(vehicleId).add(worker);
            loads.remove(vehicleId);
        }

        void transfer(Long fromId, int index, Long toId) {
            Worker moved = membersOf(fromId).remove(index);
            membersOf(toId).add(moved);
            loads.remove(fromId);
            loads.remove(toId);
        }

        void applyRelocate(Long fromId, int index, Long toId, double fromLoad, double toLoad) {
            Worker moved = membersOf(fromId).remove(index);
            membersOf(toId).add(moved);
            loads.put(fromId, fromLoad);
            loads.put(toId, toLoad);
        }

        void applySwap(Long fromId, int fromIndex, Long toId, int toIndex,
                double fromLoad, double toLoad) {
            List<Worker> source = membersOf(fromId);
            List<Worker> target = membersOf(toId);

            Worker outgoing = source.get(fromIndex);
            source.set(fromIndex, target.get(toIndex));
            target.set(toIndex, outgoing);

            loads.put(fromId, fromLoad);
            loads.put(toId, toLoad);
        }

        // -------------------------------------------------------- seçim

        /** Kapasitesi uygun servisler içinde yükü en az artıranı. */
        ServiceVehicle cheapestFor(Worker worker, int cap) {
            ServiceVehicle best = null;
            double bestCost = Double.MAX_VALUE;

            for (ServiceVehicle vehicle : vehicles) {
                int size = membersOf(vehicle.getId()).size();
                if (size >= Math.min(cap, vehicle.getCapacity())) {
                    continue;
                }

                double cost = marginalLoad(vehicle.getId(), worker);
                if (cost < bestCost) {
                    bestCost = cost;
                    best = vehicle;
                }
            }

            return best;
        }

        /** En ucuz iki servis arasındaki fark — büyükse bu kişi önce yerleşmeli. */
        double regret(Worker worker) {
            double best = Double.MAX_VALUE;
            double second = Double.MAX_VALUE;

            for (ServiceVehicle vehicle : vehicles) {
                double detour = detourKm(worker, vehicle.getId());
                if (detour < best) {
                    second = best;
                    best = detour;
                } else if (detour < second) {
                    second = detour;
                }
            }

            return second == Double.MAX_VALUE ? 0 : second - best;
        }

        /** İşçiye en ucuz düşen servisler — aramada denenecek adaylar. */
        List<ServiceVehicle> nearestVehicles(Worker worker, Long excludeVehicleId, int limit) {
            return vehicles.stream()
                    .filter(v -> !v.getId().equals(excludeVehicleId))
                    .sorted(Comparator.comparingDouble(v -> detourKm(worker, v.getId())))
                    .limit(limit)
                    .toList();
        }

        /**
         * Hedef servisteki, {@code outgoing}'den daha çok kaynak servise ait
         * görünen kişilerin indeksleri. Ön eleme olmadan takas çok pahalıya
         * gelirdi.
         */
        List<Integer> swapCandidates(Long targetVehicleId, Long sourceVehicleId, Worker outgoing) {
            List<Worker> target = membersOf(targetVehicleId);
            double outgoingDetour = detourKm(outgoing, sourceVehicleId);

            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < target.size(); i++) {
                if (detourKm(target.get(i), sourceVehicleId) < outgoingDetour) {
                    candidates.add(i);
                }
            }

            candidates.sort(Comparator.comparingDouble(i -> detourKm(target.get(i), sourceVehicleId)));
            return candidates.size() > SWAP_CANDIDATES
                    ? candidates.subList(0, SWAP_CANDIDATES)
                    : candidates;
        }

        /**
         * İşçinin bir servise ne kadar "yol üstünde" düştüğünün ucuz ölçüsü:
         * şoförün evinden ofise giderken o kişiye uğramanın getirdiği sapma.
         *
         * <p>
         * Eskiden burada işçi evi ile <b>şoförün evi</b> arasındaki kuş uçuşu
         * mesafe vardı; ofisin nerede olduğunu hiç hesaba katmıyordu. İki uç
         * arasında, tam güzergâh üstünde oturan biri ile aynı uzaklıkta ama ters
         * yönde oturan biri o ölçüde eşit görünüyordu. Sapma ölçüsü bu ikisini
         * ayırır ve matris varsa gerçek yol mesafesiyle ayırır.
         */
        double detourKm(Worker worker, Long vehicleId) {
            Driver driver = drivers.get(vehicleId);
            if (driver == null) {
                return Double.MAX_VALUE / 4;
            }

            if (matrix != null) {
                int home = matrix.driver(driver.getId());
                int stop = matrix.worker(worker.getId());

                double viaWorker = matrix.km(home, stop) + matrix.km(stop, RoadMatrix.OFFICE);
                double direct = matrix.km(home, RoadMatrix.OFFICE);

                if (Double.isFinite(viaWorker) && Double.isFinite(direct)) {
                    return viaWorker - direct;
                }
            }

            double viaWorker = GeoUtils.haversineKm(
                    driver.getLatitude(), driver.getLongitude(),
                    worker.getLatitude(), worker.getLongitude())
                    + GeoUtils.haversineKm(
                            worker.getLatitude(), worker.getLongitude(),
                            settings.getOfficeLat(), settings.getOfficeLon());
            double direct = GeoUtils.haversineKm(
                    driver.getLatitude(), driver.getLongitude(),
                    settings.getOfficeLat(), settings.getOfficeLon());

            return viaWorker - direct;
        }

        // ---------------------------------------------------------- rapor

        /**
         * Dağıtımın kural karnesi.
         *
         * <p>
         * Rakamlar <b>tahmin</b>: gerçek rota ayrıca {@code /route} çekip
         * tıkanıklığı çizginin tamamı boyunca örneklediği için biraz daha
         * yüksek çıkar. Arayüzde yazan sayı odur; burası atamanın neye bakarak
         * karar verdiğini gösteriyor.
         */
        void logRuleStatus(long elapsedMs) {
            List<String> rows = new ArrayList<>();
            int breaches = 0;

            for (ServiceVehicle vehicle : vehicles) {
                Driver driver = drivers.get(vehicle.getId());
                List<Worker> members = membersOf(vehicle.getId());

                StringBuilder row = new StringBuilder("S" + vehicle.getId() + "(" + members.size() + ")");
                for (Shift shift : Shift.values()) {
                    LoadEstimate estimate = routeService.estimateLoad(driver, members, matrix, shift);
                    int ride = (int) Math.round(estimate.maxRideMinutes());
                    boolean over = ride > rideLimit;
                    breaches += over ? 1 : 0;
                    row.append(" ").append(shift.label()).append("=").append(ride)
                            .append("/").append(Math.round(estimate.totalMinutes())).append(over ? "!" : "");
                }
                rows.add(row.toString());
            }

            log.info("Kural karnesi (tahmini en uzun yolculuk, sınır {} dk, aşan {} sefer, {} ms): {}",
                    rideLimit, breaches, elapsedMs, rows);
        }
    }
}
