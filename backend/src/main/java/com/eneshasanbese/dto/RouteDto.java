package com.eneshasanbese.dto;

import java.util.List;

/**
 * Bir servisin güzergâhı.
 *
 * @param stops    sıralı duraklar
 * @param geometry yolu takip eden çizgi ([lat, lon] noktaları). OSRM kapalıysa
 *                 null gelir; arayüz o durumda durakları düz çizgiyle birleştirir.
 */
public record RouteDto(List<RouteStopDto> stops, List<double[]> geometry) {
}
