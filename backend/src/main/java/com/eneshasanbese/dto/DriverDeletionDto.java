package com.eneshasanbese.dto;

/**
 * Şoför silme sonucu.
 *
 * @param tasinanPersonel silinen servisten kalan servislere dağıtılan kişi sayısı
 */
public record DriverDeletionDto(int tasinanPersonel) {
}
