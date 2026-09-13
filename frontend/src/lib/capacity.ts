import type { Service } from '@/types'

export type CapacityLevel = 'under' | 'ok' | 'warn' | 'full'

/**
 * Kapasite renk kodu tek yerden: yeşil (rahat) / sarı (12-14) / kırmızı (dolu).
 * Min. kapasitenin (5) altı ayrıca "under" olarak işaretlenir — otomatik
 * birleştirme yapılmaz, sadece uyarılır.
 */
export function capacityLevel(service: Service): CapacityLevel {
  const { kisiSayisi, minKapasite, maxKapasite } = service
  if (kisiSayisi >= maxKapasite) return 'full'
  if (kisiSayisi < minKapasite) return 'under'
  if (kisiSayisi >= maxKapasite - 3) return 'warn'
  return 'ok'
}

export const CAPACITY_META: Record<
  CapacityLevel,
  { label: string; bar: string; text: string; badge: string }
> = {
  ok: {
    label: 'Uygun',
    bar: 'bg-capacity-ok',
    text: 'text-capacity-ok',
    badge: 'border-capacity-ok/40 bg-capacity-ok/10 text-capacity-ok',
  },
  warn: {
    label: 'Dolmak üzere',
    bar: 'bg-capacity-warn',
    text: 'text-capacity-warn',
    badge: 'border-capacity-warn/40 bg-capacity-warn/10 text-capacity-warn',
  },
  full: {
    label: 'Dolu',
    bar: 'bg-capacity-full',
    text: 'text-capacity-full',
    badge: 'border-capacity-full/40 bg-capacity-full/10 text-capacity-full',
  },
  under: {
    label: 'Min. kapasite altında',
    bar: 'bg-capacity-under',
    text: 'text-capacity-under',
    badge: 'border-capacity-under/40 bg-capacity-under/10 text-capacity-under',
  },
}
