package com.eneshasanbese.dto;

/**
 * Ekleme/güncelleme/silme sonrası senkron sonuç: etkilenen servisin güncel hali
 * ve yeniden hesaplanmış rotası. Arayüz Redux store'unu bununla günceller.
 */
public record MutationResultDto(
        EmployeeDto employee,
        ServiceDto service,
        RouteDto route) {
}
