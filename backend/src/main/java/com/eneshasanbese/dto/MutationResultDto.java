package com.eneshasanbese.dto;

import java.util.List;

/**
 * Ekleme/güncelleme/silme sonrası senkron sonuç: etkilenen servisin güncel hali
 * ve yeniden hesaplanmış rotası. Arayüz React Query cache'ini bununla günceller.
 */
public record MutationResultDto(
        EmployeeDto employee,
        ServiceDto service,
        List<RouteStopDto> route) {
}
