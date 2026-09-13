package com.eneshasanbese.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eneshasanbese.dto.ReassignResultDto;
import com.eneshasanbese.dto.RouteDto;
import com.eneshasanbese.dto.ServiceDto;
import com.eneshasanbese.enums.Shift;
import com.eneshasanbese.service.AssignmentService;
import com.eneshasanbese.service.AssignmentService.ReassignSummary;
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
    public List<ServiceDto> list(@RequestParam(defaultValue = "sabah") String sefer) {
        return serviceCatalogService.listServices(Shift.of(sefer));
    }

    @GetMapping("/{id}")
    public ServiceDto detail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "sabah") String sefer) {
        return serviceCatalogService.serviceOf(id, Shift.of(sefer));
    }

    @GetMapping("/{id}/route")
    public RouteDto route(
            @PathVariable Long id,
            @RequestParam(defaultValue = "sabah") String sefer) {
        return serviceCatalogService.routeOf(id, Shift.of(sefer));
    }

    /**
     * Bütün atamaları sıfırlayıp baştan dağıtır. Dashboard'daki "Yeniden dağıt"
     * düğmesinin karşılığı.
     *
     * <p>
     * Personel ekleme/silme gibi olaylarda kendiliğinden çağrılmaz: sonucu
     * genellikle daha iyi olsa da neredeyse herkesin servisini değiştirebildiği
     * için kararı kullanıcıya bırakıyoruz. Gündelik akışta işleyen, mevcut
     * dağılımı koruyan {@code rebalance}.
     *
     * <p>
     * {@code sefer} yalnızca <em>yanıttaki</em> özetin hangi sefere ait olacağını
     * belirler; dağıtım kararı her iki seferi birden ölçer.
     */
    @PostMapping("/reassign")
    public ReassignResultDto reassign(@RequestParam(defaultValue = "sabah") String sefer) {
        ReassignSummary summary = assignmentService.reassignAll();
        return new ReassignResultDto(
                summary.total(),
                summary.moved(),
                serviceCatalogService.listServices(Shift.of(sefer)));
    }
}
