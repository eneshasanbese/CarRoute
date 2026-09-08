package com.eneshasanbese.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eneshasanbese.dto.RouteStopDto;
import com.eneshasanbese.dto.ServiceDto;
import com.eneshasanbese.service.AssignmentService;
import com.eneshasanbese.service.ServiceCatalogService;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceCatalogService serviceCatalogService;
    private final AssignmentService assignmentService;

    public ServiceController(
            ServiceCatalogService serviceCatalogService,
            AssignmentService assignmentService) {
        this.serviceCatalogService = serviceCatalogService;
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public List<ServiceDto> list() {
        return serviceCatalogService.listServices();
    }

    @GetMapping("/{id}")
    public ServiceDto detail(@PathVariable Long id) {
        return serviceCatalogService.serviceOf(id);
    }

    @GetMapping("/{id}/route")
    public List<RouteStopDto> route(@PathVariable Long id) {
        return serviceCatalogService.routeOf(id);
    }

    /**
     * Bütün atamaları sıfırlayıp baştan dağıtır. Arayüzde karşılığı yok; elle
     * tetiklemek (veya seed sonrası) için.
     */
    @PostMapping("/reassign")
    public Map<String, Object> reassign() {
        int count = assignmentService.reassignAll();
        return Map.of("reassigned", count, "services", serviceCatalogService.listServices());
    }
}
