package com.eneshasanbese.dto;

import java.util.List;

/**
 * "Yeniden dağıt" işleminin sonucu.
 *
 * <p>
 * Güncel servis listesi de yanıtta dönüyor; arayüz dağıtımdan hemen sonra ayrı
 * bir {@code GET /api/services} atmak zorunda kalmasın diye. Aynı kalıp
 * {@link MutationResultDto} içinde de var.
 *
 * @param toplamPersonel  kayıtlı toplam personel
 * @param servisiDegisen  bunların kaçı başka bir servise geçti — kullanıcıya
 *                        anlamlı gelen sayı bu
 * @param servisler       dağıtımdan sonraki servis özetleri, istenen sefer için
 */
public record ReassignResultDto(
        int toplamPersonel,
        int servisiDegisen,
        List<ServiceDto> servisler) {
}
