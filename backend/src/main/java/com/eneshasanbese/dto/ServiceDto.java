package com.eneshasanbese.dto;

//Servisin karşılığı
public record ServiceDto(
                Long id,
                int kisiSayisi,
                int minKapasite,
                int maxKapasite,
                double toplamKm,
                int tahminiSureDk,
                String kalkisSaati) {
}
