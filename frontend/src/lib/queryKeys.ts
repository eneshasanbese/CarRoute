import type { TrafficBucket } from '@/types'

export const queryKeys = {
  employees: ['employees'] as const,
  services: ['services'] as const,
  serviceRoute: (id: number) => ['services', id, 'route'] as const,
  traffic: (bucket: TrafficBucket) => ['traffic', bucket] as const,
}

/**
 * Ekleme/silme sonrası rota değişikliğinin arayüze yansıması için kısa aralıklı
 * polling. WebSocket eklenirse bu sabit kaldırılıp soket olayları ile
 * `invalidateQueries` tetiklenmeli.
 */
export const POLL_INTERVAL_MS = 5000
