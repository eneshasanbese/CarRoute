package com.eneshasanbese.dto;

/**
 * Bir zirve diliminin şehir geneli trafik özeti.
 *
 * @param yogunlukYuzde zirvenin gece serbest akışına göre yavaşlaması (0–100)
 * @param saatAraligi   dilimin kapsadığı saatler, ör. "06:00–08:00"
 * @param kaynak        verinin kaynağı ve dönemi — veri canlı değil, statik
 */
public record TrafficSnapshotDto(
        String bucket,
        int yogunlukYuzde,
        String saatAraligi,
        String kaynak) {
}
