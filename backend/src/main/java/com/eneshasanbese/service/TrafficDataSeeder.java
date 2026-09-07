package com.eneshasanbese.service;

import com.eneshasanbese.entity.TrafficSpeed;
import com.eneshasanbese.repository.TrafficSpeedRepository;
import com.eneshasanbese.util.Accumulator;
import com.opencsv.CSVReader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class TrafficDataSeeder implements CommandLineRunner {

    @Autowired
    private final TrafficSpeedRepository repository;

    public TrafficDataSeeder(TrafficSpeedRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) {
            System.out.println("Trafik verisi zaten yüklü, atlıyorum.");
            return;
        }

        Map<String, Map<String, Accumulator>> summary = new HashMap<>();
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

                summary
                        .computeIfAbsent(geohash, k -> new HashMap<>())
                        .computeIfAbsent(timeSlot, k -> new Accumulator())
                        .add(avgSpeed, vehicleCount);

                processedCount++;
                if (processedCount % 100_000 == 0) {
                    System.out.println(processedCount + " satır işlendi...");
                }
            }
        }

        saveToDatabase(summary);
        System.out.println("Trafik hız tablosu DB'ye kaydedildi. Toplam geohash: " + summary.size());
    }

    private void saveToDatabase(Map<String, Map<String, Accumulator>> summary) {
        for (Map.Entry<String, Map<String, Accumulator>> geoEntry : summary.entrySet()) {
            String geohash = geoEntry.getKey();

            for (Map.Entry<String, Accumulator> slotEntry : geoEntry.getValue().entrySet()) {
                TrafficSpeed entity = new TrafficSpeed();
                entity.setGeohash(geohash);
                entity.setTimeSlot(slotEntry.getKey());
                entity.setAvgSpeed(slotEntry.getValue().average());
                repository.save(entity);
            }
        }
    }

    private String getTimeSlot(int hour, int minute) {
        int totalMinutes = hour * 60 + minute;

        int sabahBaslangic = 6 * 60; // 06:00
        int sabahBitis = 8 * 60; // 08:00
        int aksamBaslangic = 17 * 60 + 30; // 17:30
        int aksamBitis = 19 * 60; // 19:00

        if (totalMinutes >= sabahBaslangic && totalMinutes < sabahBitis) {
            return "SABAH_ZIRVE";
        }
        if (totalMinutes >= aksamBaslangic && totalMinutes < aksamBitis) {
            return "AKSAM_ZIRVE";
        }
        return null;
    }
}