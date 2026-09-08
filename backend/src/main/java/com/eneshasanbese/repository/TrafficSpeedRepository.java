package com.eneshasanbese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.eneshasanbese.entity.TrafficSpeed;

@Repository
public interface TrafficSpeedRepository extends JpaRepository<TrafficSpeed, Long> {
    Optional<TrafficSpeed> findByGeohashAndTimeSlot(String geohash, String timeSlot);

    List<TrafficSpeed> findByTimeSlot(String timeSlot);
}
