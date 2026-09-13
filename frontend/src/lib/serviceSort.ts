import type { Service } from '@/types'

export type ServiceSortKey =
  | 'servis'
  | 'sure-artan'
  | 'sure-azalan'
  | 'km-artan'
  | 'km-azalan'
  | 'doluluk-azalan'
  | 'doluluk-artan'

export const SERVICE_SORT_OPTIONS: Array<{
  value: ServiceSortKey
  label: string
}> = [
  { value: 'servis', label: 'Servis no' },
  { value: 'sure-artan', label: 'Süre: kısadan uzuna' },
  { value: 'sure-azalan', label: 'Süre: uzundan kısaya' },
  { value: 'km-artan', label: 'Mesafe: azdan çoğa' },
  { value: 'km-azalan', label: 'Mesafe: çoktan aza' },
  { value: 'doluluk-azalan', label: 'Doluluk: çoktan aza' },
  { value: 'doluluk-artan', label: 'Doluluk: azdan çoğa' },
]

/**
 * Doluluk oranı — kişi sayısı değil. Araçların kapasitesi farklı olabildiği
 * için 12/15 ile 12/20'yi aynı saymak yanıltıcı olurdu.
 */
function dolulukOrani(service: Service) {
  return service.maxKapasite > 0 ? service.kisiSayisi / service.maxKapasite : 0
}

const KARSILASTIRICILAR: Record<
  ServiceSortKey,
  (a: Service, b: Service) => number
> = {
  servis: (a, b) => a.id - b.id,
  'sure-artan': (a, b) => a.tahminiSureDk - b.tahminiSureDk,
  'sure-azalan': (a, b) => b.tahminiSureDk - a.tahminiSureDk,
  'km-artan': (a, b) => a.toplamKm - b.toplamKm,
  'km-azalan': (a, b) => b.toplamKm - a.toplamKm,
  'doluluk-azalan': (a, b) => dolulukOrani(b) - dolulukOrani(a),
  'doluluk-artan': (a, b) => dolulukOrani(a) - dolulukOrani(b),
}

/**
 * Sıralanmış kopya döndürür; girdi dizisi Redux store'undan geldiği için yerinde
 * sıralamak dondurulmuş state'i değiştirmeye kalkardı.
 *
 * <p>
 * Eşitlikte servis numarasına düşülüyor — aksi halde iki servisin süresi aynı
 * olduğunda sıra her poll'da oynayıp kartlar yer değiştiriyordu.
 */
export function sortServices(
  services: Service[],
  key: ServiceSortKey,
): Service[] {
  const karsilastir = KARSILASTIRICILAR[key]
  return [...services].sort((a, b) => karsilastir(a, b) || a.id - b.id)
}
