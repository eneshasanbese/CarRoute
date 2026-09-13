export type Cinsiyet = 'Kadın' | 'Erkek'
export type TrafficBucket = 'sabah' | 'aksam'

/** Servis seferi. Sabah ev->ofis, akşam ofis->ev. */
export type Sefer = 'sabah' | 'aksam'

export interface Employee {
  id: number
  adSoyad: string
  cinsiyet: Cinsiyet
  yas: number
  /** Kayıtlı değilse null — mevcut seed satırlarında telefon yok. */
  telefon: string | null
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
  /**
   * Aracın plakası; arayüzde servisin adı budur. Id silme/eklemeyle boşluklu
   * ilerlediği için ad olarak gösterilmiyor — bkz. lib/serviceName.ts.
   */
  plaka: string | null
  sefer: Sefer
  kisiSayisi: number
  minKapasite: number // 5
  maxKapasite: number // 15
  toplamKm: number
  tahminiSureDk: number
  /** Sabah: hesaplanan kalkış. Akşam: sabit ofis kalkışı (17:30). */
  kalkisSaati: string
  /** Sabah: ofise varış (08:00). Akşam: son yolcunun indiği saat. */
  varisSaati: string
  /** En uzun süre araçta kalan yolcunun süresi. Şoför sayılmaz. */
  enUzunYolculukDk: number
  /** İzin verilen üst sınır (varsayılan 90). */
  azamiYolculukDk: number
  kuralIhlali: boolean
}

export interface RouteStop {
  servisId: number
  durakNo: number
  employeeId: number | null // null ise bu durak ofis/garaj
  adSoyad: string
  lat: number
  lon: number
  oncekiDuraktanKm: number
  /**
   * Tahmini varış penceresinin başı ve sonu ("06:58" / "07:04").
   *
   * Tek bir dakika yerine aralık veriliyor: genişliği İBB verisinin günden güne
   * oynaklığından türetiliyor ve yol aldıkça birikiyor. İkisi eşitse pencere
   * sıfır demektir (kalkış durağı).
   */
  varisSaatiErken: string
  varisSaatiGec: string
  /** Bu yolcunun araçta geçirdiği süre. Depo duraklarında 0. */
  yolculukDk: number
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
  /** Zirvenin gece serbest akışına göre yavaşlaması (0–100). */
  yogunlukYuzde: number
  /** Dilimin kapsadığı saatler, ör. "06:00–08:00". */
  saatAraligi: string
  /** Verinin kaynağı ve dönemi — veri canlı değil, statik. */
  kaynak: string
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
  telefon?: string
  ilce?: string
  lat?: number
  lon?: number
}

/**
 * Ekleme/güncelleme/silme sonrası backend'in döndürdüğü senkron sonuç:
 * etkilenen servisin güncel hali + yeniden hesaplanmış rotası. Servise
 * atanmamış biri silindiğinde yeniden hesaplanacak rota olmadığı için `service`
 * ve `route` null gelir.
 */
export interface MutationResult {
  employee: Employee | null
  service: Service | null
  route: ServiceRoute | null
}

/**
 * "Yeniden dağıt" sonucu: dağıtımdan sonraki servis listesi ve kaç kişinin
 * servisinin değiştiği.
 */
export interface ReassignResult {
  toplamPersonel: number
  servisiDegisen: number
  servisler: Service[]
}

export interface GeocodeResult {
  label: string
  ilce: string
  lat: number
  lon: number
}

/**
 * Şoför ve sürdüğü servis. Sistemde ikisi ayrılmaz: rotanın başlangıç noktası
 * şoförün ev adresi olduğu için şoförsüz servis ya da servissiz şoför anlamsız.
 */
export interface Driver {
  id: number
  adSoyad: string
  telefon: string | null
  adres: string
  ilce: string
  lat: number
  lon: number
  servisId: number | null
  plaka: string | null
  model: string | null
  kapasite: number
}

/**
 * Şoför ekleme/güncelleme gövdesi. Eklemede yeni bir servis de oluşur. Plaka
 * zorunlu çünkü servisin adı; telefon boş bırakılırsa kayıtlı olan korunur.
 */
export interface DriverInput {
  adSoyad: string
  adres: string
  telefon?: string
  plaka: string
  model?: string
  ilce?: string
  lat?: number
  lon?: number
}

/** Şoför silme sonucu: silinen servisten kalan servislere dağıtılan kişi sayısı. */
export interface DriverDeletionResult {
  tasinanPersonel: number
}
