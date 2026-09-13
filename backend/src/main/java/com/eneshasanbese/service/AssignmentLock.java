package com.eneshasanbese.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

/**
 * Personel ve servis dağılımını değiştiren işlemleri sıraya sokar.
 *
 * <p>
 * <b>Neden gerekli.</b> Atama önce mevcut dağılımı okuyor, karar veriyor, sonra
 * yazıyor. İki işlem aynı anda koşarsa ikisi de aynı eski tabloyu görür: 14/15
 * dolu bir servise iki kişi birden yerleşip 16 olur, ya da "Yeniden dağıt"
 * sürerken eklenen kişi dağıtımın üzerine yazılır.
 *
 * <p>
 * <b>Neden Java kilidi değil.</b> {@code synchronized} ya da
 * {@code ReentrantLock} metot bitince bırakılır, transaction ise ondan
 * <em>sonra</em> commit edilir. Arada ikinci işlem kilidi alıp henüz commit
 * edilmemiş, eski tabloyu okur. PostgreSQL'in {@code pg_advisory_xact_lock}
 * kilidi transaction'a bağlı: commit ya da rollback anında kendiliğinden
 * bırakılır. Aynı transaction içinde tekrar alınabilir, dolayısıyla iç içe
 * çağrılar kendini kilitlemez.
 */
@Component
public class AssignmentLock {

    /** Uygulamaya özgü, keyfi ama sabit kilit anahtarı. */
    private static final long LOCK_KEY = 0x43415252_4F555445L;

    private final EntityManager entityManager;

    public AssignmentLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Kilidi alır; başka bir işlem tutuyorsa onun transaction'ı bitene kadar
     * bekler. Okumalardan <b>önce</b> çağrılmalı — kilitten önce okunan veri
     * zaten bayat olabilir.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        // Fonksiyon void döndürdüğü için doğrudan SELECT edilemiyor; FROM içinde
        // çağırıp sabit bir değer okuyoruz.
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:key)")
                .setParameter("key", LOCK_KEY)
                .getSingleResult();
    }
}
