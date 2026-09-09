export type Cinsiyet = 'Kadın' | 'Erkek'
export type TrafficBucket = 'sabah' | 'aksam'

export interface Employee {
  id: number
  adSoyad: string
  cinsiyet: Cinsiyet
  yas: number
  adres: string
  ilce: string
  lat: number
  lon: number
  arabaliMi: boolean
  cocukVarMi: boolean
  /** Henüz bir servise atanmamışsa null. */
  servisId: number | null
}

export interface Service {
  id: number
  kisiSayisi: number
  minKapasite: number // 5
  maxKapasite: number // 15
  toplamKm: number
  tahminiSureDk: number
  kalkisSaati: string // "06:16"
}

export interface RouteStop {
  servisId: number
  durakNo: number
  employeeId: number | null // null ise bu durak ofis/garaj
  adSoyad: string
  lat: number
  lon: number
  oncekiDuraktanKm: number
}

/** Harita için yol çizgisi noktası: [lat, lon]. */
export type LatLon = [number, number]

/**
 * Bir servisin güzergâhı. `geometry` yolu takip eden çizgidir; backend'de OSRM
 * kapalıysa null gelir ve harita durakları düz çizgiyle birleştirir.
 */
export interface ServiceRoute {
  stops: RouteStop[]
  geometry: LatLon[] | null
}

export interface TrafficSnapshot {
  bucket: TrafficBucket
  yogunlukYuzde: number
  guncellemeZamani: string
}

/**
 * Personel ekleme/güncelleme gövdesi.
 *
 * Spec'teki POST kontratına ek olarak `adSoyad` (tabloda ve formda zorunlu bir
 * alan) ve autocomplete'ten seçilen `lat`/`lon`/`ilce` de gönderiliyor. Çocuk
 * bilgisi sistemde yalnızca var/yok olarak tutuluyor, sayı tutulmuyor.
 * Koordinatlar opsiyoneldir: kullanıcı listeden seçim yapmadıysa gönderilmez ve
 * adresi backend'in geocode etmesi beklenir.
 */
export interface EmployeeInput {
  adSoyad: string
  adres: string
  cinsiyet: Cinsiyet
  yas: number
  arabaliMi: boolean
  cocukVarMi: boolean
  ilce?: string
  lat?: number
  lon?: number
}

/**
 * Ekleme/güncelleme/silme sonrası backend'in döndürdüğü senkron sonuç:
 * etkilenen servisin güncel hali + yeniden hesaplanmış rotası.
 */
export interface MutationResult {
  employee: Employee | null
  service: Service
  route: ServiceRoute
}

export interface GeocodeResult {
  label: string
  ilce: string
  lat: number
  lon: number
}
