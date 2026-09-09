package com.eneshasanbese.service;

import com.eneshasanbese.entity.TrafficSpeed;
import com.eneshasanbese.repository.TrafficSpeedRepository;
import com.eneshasanbese.util.SpeedStats;
import com.opencsv.CSVReader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(1)
public class TrafficDataSeeder implements CommandLineRunner {

    @Autowired
    private final TrafficSpeedRepository repository;

    public TrafficDataSeeder(TrafficSpeedRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Beklenen dilimlerin hepsi yüklü mü? Gece dilimi sonradan eklendiği için
        // eski bir veritabanında sabah/akşam dolu, gece boş olabilir; o durumda
        // tablo türetilmiş veri olduğundan tamamı yeniden üretilir.
        boolean eksikDilimVar = repository.countByTimeSlot(TrafficSpeedService.SLOT_FREE_FLOW) == 0
                || repository.countByTimeSlot(TrafficSpeedService.SLOT_MORNING) == 0
                || repository.countByTimeSlot(TrafficSpeedService.SLOT_EVENING) == 0;

        // Oynaklık kolonu sonradan eklendi; hiç dolmamışsa tablo eski demektir.
        boolean oynaklikEksik = repository.countBySpeedVariationGreaterThan(0) == 0;

        if (!eksikDilimVar && !oynaklikEksik) {
            System.out.println("Trafik verisi zaten yüklü, atlıyorum.");
            return;
        }

        if (repository.count() > 0) {
            System.out.println("Trafik tablosu eksik ("
                    + (eksikDilimVar ? "zaman dilimi" : "oynaklık verisi")
                    + "), tamamı yeniden üretiliyor...");
            repository.deleteAllInBatch();
        }

        Map<String, Map<String, SpeedStats>> summary = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        ClassPathResource resource = new ClassPathResource("traffic_density_202501.csv");
        try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream()))) {
            reader.readNext(); // header'ı atla
            String[] line;
            long processedCount = 0;

            while ((line = reader.readNext()) != null) {
                LocalDateTime dateTime = LocalDateTime.parse(line[0], formatter);
                String timeSlot = getTimeSlot(dateTime.getHour(), dateTime.getMinute());

                if (timeSlot == null)
                    continue; // sabah/akşam zirve dışındaki satırları atla

                String geohash = line[3];
                double avgSpeed = Double.parseDouble(line[6]);
                int vehicleCount = Integer.parseInt(line[7]);
                // Oynaklık gün bazında ölçüldüğü için tarih de taşınıyor.
                String day = dateTime.toLocalDate().toString();

                summary
                        .computeIfAbsent(geohash, k -> new HashMap<>())
                        .computeIfAbsent(timeSlot, k -> new SpeedStats())
                        .add(day, avgSpeed, vehicleCount);

                processedCount++;
                if (processedCount % 100_000 == 0) {
                    System.out.println(processedCount + " satır işlendi...");
                }
            }
        }

        saveToDatabase(summary);
        System.out.println("Trafik hız tablosu DB'ye kaydedildi. Toplam geohash: " + summary.size());
    }

    private void saveToDatabase(Map<String, Map<String, SpeedStats>> summary) {
        List<TrafficSpeed> batch = new ArrayList<>();

        for (Map.Entry<String, Map<String, SpeedStats>> geoEntry : summary.entrySet()) {
            String geohash = geoEntry.getKey();

            for (Map.Entry<String, SpeedStats> slotEntry : geoEntry.getValue().entrySet()) {
                SpeedStats stats = slotEntry.getValue();

                TrafficSpeed entity = new TrafficSpeed();
                entity.setGeohash(geohash);
                entity.setTimeSlot(slotEntry.getKey());
                entity.setAvgSpeed(stats.average());
                entity.setSpeedVariation(stats.variation());
                batch.add(entity);
            }
        }

        repository.saveAll(batch);
    }

    /**
     * Bir ölçüm satırının hangi zaman dilimine düştüğü; dilim dışıysa null.
     *
     * <p>
     * <b>Gece dilimi neden var:</b> tıkanıklık ancak bir referansa göre
     * ölçülebilir. Her hücrenin gece hızı, o hücrenin <em>kendi</em> serbest akış
     * hızıdır — ara sokak için ~40, otoyol için ~110. Zirve hızını buna
     * bölünce çıkan oran, mutlak hızın aksine yollar arasında taşınabilir bir
     * büyüklüktür. {@code TrafficSpeedService.congestionFactor} bunu kullanır.
     *
     * <p>
     * 01:00–05:00 seçildi: trafik en seyrek, ama ölçüm yapacak kadar araç var.
     */
    private String getTimeSlot(int hour, int minute) {
        int totalMinutes = hour * 60 + minute;

        int geceBaslangic = 1 * 60; // 01:00
        int geceBitis = 5 * 60; // 05:00
        int sabahBaslangic = 6 * 60; // 06:00
        int sabahBitis = 8 * 60; // 08:00
        int aksamBaslangic = 17 * 60 + 30; // 17:30
        int aksamBitis = 19 * 60; // 19:00

        if (totalMinutes >= geceBaslangic && totalMinutes < geceBitis) {
            return TrafficSpeedService.SLOT_FREE_FLOW;
        }
        if (totalMinutes >= sabahBaslangic && totalMinutes < sabahBitis) {
            return TrafficSpeedService.SLOT_MORNING;
        }
        if (totalMinutes >= aksamBaslangic && totalMinutes < aksamBitis) {
            return TrafficSpeedService.SLOT_EVENING;
        }
        return null;
    }
}