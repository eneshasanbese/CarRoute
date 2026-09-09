package com.eneshasanbese.dto;

// Duraklar. employeeId null ise durak boş, ofis veya kalkış noktasıdır.
public record RouteStopDto(
                Long servisId,
                int durakNo,
                Long employeeId,
                String adSoyad,
                double lat,
                double lon,
                double oncekiDuraktanKm) {
}
