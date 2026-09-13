package com.eneshasanbese.dto;

//Empolyee karşılığı
public record EmployeeDto(
                Long id,
                String adSoyad,
                String cinsiyet,
                int yas,
                String telefon,
                String adres,
                String ilce,
                double lat,
                double lon,
                boolean arabaliMi,
                boolean cocukVarMi,
                Long servisId) {
}
