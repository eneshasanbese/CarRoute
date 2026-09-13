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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eneshasanbese.dto.EmployeeDto;
import com.eneshasanbese.dto.EmployeeRequest;
import com.eneshasanbese.dto.MutationResultDto;
import com.eneshasanbese.enums.Shift;
import com.eneshasanbese.service.EmployeeService;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public List<EmployeeDto> list() {
        return employeeService.findAll();
    }

    /**
     * Değişiklik yanıtları etkilenen servisin rotasını da taşır; {@code sefer}
     * o rotanın arayüzde açık olan sefere ait olmasını sağlar.
     */
    @PostMapping
    public ResponseEntity<MutationResultDto> create(
            @RequestBody EmployeeRequest request,
            @RequestParam(defaultValue = "sabah") String sefer) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.create(request, Shift.of(sefer)));
    }

    @PutMapping("/{id}")
    public MutationResultDto update(
            @PathVariable Long id,
            @RequestBody EmployeeRequest request,
            @RequestParam(defaultValue = "sabah") String sefer) {
        return employeeService.update(id, request, Shift.of(sefer));
    }

    @DeleteMapping("/{id}")
    public MutationResultDto delete(
            @PathVariable Long id,
            @RequestParam(defaultValue = "sabah") String sefer) {
        return employeeService.delete(id, Shift.of(sefer));
    }
}
