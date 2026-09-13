package com.eneshasanbese.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.eneshasanbese.entity.Worker;
import com.eneshasanbese.repository.WorkerRepository;
import com.eneshasanbese.util.AddressUtils;

/**
 * Bir adresi koordinata çevirir.
 *
 * <p>
 * Arayüz adres autocomplete'inden seçim yapıldıysa koordinat gövdede gelir ve
 * burası hiç devreye girmez. Kullanıcı adresi elle yazdıysa, aynı ilçedeki
 * mevcut personelin ağırlık merkezi kullanılır — harici bir geocoding servisine
 * sunucu tarafında bağımlılık eklememek için kasıtlı olarak böyle.
 *
 * <p>
 * Hem personel hem şoför aynı çözümü kullanıyor; ikisi de aynı şehirde aynı
 * adres formatıyla giriliyor.
 */
@Service
public class LocationResolver {

    private final WorkerRepository workerRepository;

    public LocationResolver(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    /**
     * @param lat     autocomplete'ten geldiyse dolu
     * @param lon     autocomplete'ten geldiyse dolu
     * @param district istekte belirtilen ilçe; boşsa adresten çıkarılır
     * @param address  serbest yazılmış adres
     * @return [lat, lon]
     * @throws IllegalArgumentException ilçe belirlenemezse ya da o ilçede
     *                                  referans nokta yoksa
     */
    public double[] resolve(Double lat, Double lon, String district, String address) {
        if (lat != null && lon != null && Double.isFinite(lat) && Double.isFinite(lon)) {
            return new double[] { lat, lon };
        }

        String resolved = (district != null && !district.isBlank())
                ? district.trim()
                : AddressUtils.extractDistrict(address);

        if (resolved.isBlank()) {
            throw new IllegalArgumentException(
                    "Adresin koordinatı belirlenemedi. Adres listesinden bir sonuç seçin.");
        }

        List<Worker> sameDistrict = workerRepository.findAll().stream()
                .filter(w -> resolved.equalsIgnoreCase(AddressUtils.extractDistrict(w.getAddress())))
                .toList();

        if (sameDistrict.isEmpty()) {
            throw new IllegalArgumentException(
                    "\"" + resolved + "\" ilçesi için referans konum yok. Adres listesinden bir sonuç seçin.");
        }

        return new double[] {
                sameDistrict.stream().mapToDouble(Worker::getLatitude).average().orElseThrow(),
                sameDistrict.stream().mapToDouble(Worker::getLongitude).average().orElseThrow()
        };
    }
}
