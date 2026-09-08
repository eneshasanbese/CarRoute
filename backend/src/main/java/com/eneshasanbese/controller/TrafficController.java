package com.eneshasanbese.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eneshasanbese.dto.TrafficSnapshotDto;
import com.eneshasanbese.service.TrafficSpeedService;

@RestController
@RequestMapping("/api/traffic")
public class TrafficController {

    private final TrafficSpeedService trafficSpeedService;

    public TrafficController(TrafficSpeedService trafficSpeedService) {
        this.trafficSpeedService = trafficSpeedService;
    }

    @GetMapping("/snapshot")
    public TrafficSnapshotDto snapshot(@RequestParam(defaultValue = "sabah") String bucket) {
        return trafficSpeedService.snapshot(bucket);
    }
}
