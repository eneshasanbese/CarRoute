package com.eneshasanbese.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eneshasanbese.dto.DriverDeletionDto;
import com.eneshasanbese.dto.DriverDto;
import com.eneshasanbese.dto.DriverRequest;
import com.eneshasanbese.service.DriverService;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    public List<DriverDto> list() {
        return driverService.findAll();
    }

    /**
     * Şoför ve servisini birlikte oluşturur, ardından dağıtımı dengeler. Yanıt
     * yalnızca şoförü döndürür; arayüz servis listesini ayrıca tazeler çünkü
     * dengeleme birden fazla servisi etkileyebiliyor.
     */
    @PostMapping
    public ResponseEntity<DriverDto> create(@RequestBody DriverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(driverService.create(request));
    }

    /**
     * Şoförü ve aracını günceller. Ev adresi değiştiyse dağıtım dengelenir;
     * arayüz servis ve personel listesini ayrıca tazeler.
     */
    @PutMapping("/{id}")
    public DriverDto update(@PathVariable Long id, @RequestBody DriverRequest request) {
        return driverService.update(id, request);
    }

    /**
     * Şoförü ve sürdüğü servisi siler; o servisin yolcuları kalan servislere
     * dağıtılır. Kalan kapasite yetmiyorsa 409 döner.
     */
    @DeleteMapping("/{id}")
    public DriverDeletionDto delete(@PathVariable Long id) {
        return driverService.delete(id);
    }
}
