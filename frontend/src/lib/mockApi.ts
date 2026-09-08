/**
 * Tarayıcı içi sahte backend. Sadece `VITE_USE_MOCK=true` iken devreye girer;
 * gerçek backend hazır olduğunda `.env` üzerinden kapatılır ve bu dosya hiçbir
 * yerden çağrılmaz. Amacı arayüzü backend'siz çalıştırabilmek.
 *
 * Not: personel konumları İstanbul geneline rastgele dağıtılır — servis
 * kümeleri önceden "güzel" olacak şekilde yerleştirilmez, durak sırası sahte de
 * olsa gerçek bir en-yakın-komşu geçişiyle bulunur.
 */
import type {
  Employee,
  EmployeeInput,
  MutationResult,
  RouteStop,
  Service,
  TrafficBucket,
  TrafficSnapshot,
} from '@/types'

export const GARAJ = { lat: 41.0553, lon: 28.7419, ad: 'Garaj' }
export const OFIS = { lat: 41.1085, lon: 29.0201, ad: 'Ofis' }

const SERVIS_SAYISI = 10
const MIN_KAPASITE = 5
const MAX_KAPASITE = 15
const ORTALAMA_HIZ_KMH = 32

const ILCELER: Array<{ ad: string; lat: number; lon: number }> = [
  { ad: 'Kadıköy', lat: 40.99, lon: 29.03 },
  { ad: 'Üsküdar', lat: 41.023, lon: 29.015 },
  { ad: 'Maltepe', lat: 40.935, lon: 29.13 },
  { ad: 'Ataşehir', lat: 40.993, lon: 29.127 },
  { ad: 'Beşiktaş', lat: 41.043, lon: 29.006 },
  { ad: 'Şişli', lat: 41.06, lon: 28.987 },
  { ad: 'Bakırköy', lat: 40.98, lon: 28.872 },
  { ad: 'Bahçelievler', lat: 41.0, lon: 28.86 },
  { ad: 'Esenyurt', lat: 41.034, lon: 28.68 },
  { ad: 'Beylikdüzü', lat: 41.001, lon: 28.641 },
  { ad: 'Kartal', lat: 40.888, lon: 29.19 },
  { ad: 'Pendik', lat: 40.877, lon: 29.256 },
  { ad: 'Ümraniye', lat: 41.026, lon: 29.1 },
  { ad: 'Kağıthane', lat: 41.085, lon: 28.972 },
  { ad: 'Bağcılar', lat: 41.039, lon: 28.856 },
  { ad: 'Sarıyer', lat: 41.167, lon: 29.057 },
  { ad: 'Zeytinburnu', lat: 40.993, lon: 28.902 },
  { ad: 'Sultangazi', lat: 41.106, lon: 28.873 },
]

const KADIN_ADLARI = [
  'Elif', 'Zeynep', 'Merve', 'Ayşe', 'Fatma', 'Selin', 'Büşra', 'Esra',
  'Hatice', 'Gamze', 'Derya', 'Sema', 'Nur', 'Pınar', 'Ebru', 'Şeyma',
]
const ERKEK_ADLARI = [
  'Mehmet', 'Mustafa', 'Ahmet', 'Emre', 'Burak', 'Serkan', 'Hakan', 'Onur',
  'Kerem', 'Yusuf', 'Furkan', 'Barış', 'Volkan', 'Deniz', 'Tolga', 'Cem',
]
const SOYADLAR = [
  'Yılmaz', 'Kaya', 'Demir', 'Şahin', 'Çelik', 'Yıldız', 'Yıldırım', 'Öztürk',
  'Aydın', 'Özdemir', 'Arslan', 'Doğan', 'Kılıç', 'Aslan', 'Çetin', 'Kara',
  'Koç', 'Kurt', 'Özkan', 'Şimşek',
]

const SOKAKLAR = [
  'Atatürk Cad.', 'Cumhuriyet Sok.', 'Bağdat Cad.', 'İnönü Cad.', 'Gül Sok.',
  'Menekşe Sok.', 'Fatih Cad.', 'Barbaros Bulv.', 'Zafer Sok.', 'Çınar Sok.',
]

function rnd(min: number, max: number) {
  return Math.random() * (max - min) + min
}

function pick<T>(list: readonly T[]): T {
  return list[Math.floor(Math.random() * list.length)]!
}

function haversineKm(
  a: { lat: number; lon: number },
  b: { lat: number; lon: number },
) {
  const R = 6371
  const dLat = ((b.lat - a.lat) * Math.PI) / 180
  const dLon = ((b.lon - a.lon) * Math.PI) / 180
  const lat1 = (a.lat * Math.PI) / 180
  const lat2 = (b.lat * Math.PI) / 180
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.sin(dLon / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2)
  return 2 * R * Math.asin(Math.sqrt(h))
}

let nextEmployeeId = 1

function createEmployeeRecord(servisId: number): Employee {
  const ilce = pick(ILCELER)
  const cinsiyet = Math.random() < 0.48 ? 'Kadın' : 'Erkek'
  const ad = pick(cinsiyet === 'Kadın' ? KADIN_ADLARI : ERKEK_ADLARI)
  const cocukVarMi = Math.random() < 0.42
  return {
    id: nextEmployeeId++,
    adSoyad: `${ad} ${pick(SOYADLAR)}`,
    cinsiyet,
    yas: Math.floor(rnd(22, 58)),
    adres: `${ilce.ad}, ${pick(SOKAKLAR)} No:${Math.floor(rnd(1, 120))}`,
    ilce: ilce.ad,
    lat: ilce.lat + rnd(-0.028, 0.028),
    lon: ilce.lon + rnd(-0.035, 0.035),
    arabaliMi: Math.random() < 0.22,
    cocukVarMi,
    servisId,
  }
}

const employees: Employee[] = []
for (let servisId = 1; servisId <= SERVIS_SAYISI; servisId++) {
  const kisi = Math.floor(rnd(3, MAX_KAPASITE + 1))
  for (let i = 0; i < kisi; i++) {
    employees.push(createEmployeeRecord(servisId))
  }
}

/** Basit en-yakın-komşu: garajdan başla, ofiste bitir. */
function buildRoute(servisId: number): RouteStop[] {
  const kalan = employees.filter((e) => e.servisId === servisId)
  const stops: RouteStop[] = [
    {
      servisId,
      durakNo: 0,
      employeeId: null,
      adSoyad: GARAJ.ad,
      lat: GARAJ.lat,
      lon: GARAJ.lon,
      oncekiDuraktanKm: 0,
    },
  ]

  let current = { lat: GARAJ.lat, lon: GARAJ.lon }
  let durakNo = 1
  while (kalan.length > 0) {
    let bestIndex = 0
    let bestKm = Infinity
    kalan.forEach((aday, index) => {
      const km = haversineKm(current, aday)
      if (km < bestKm) {
        bestKm = km
        bestIndex = index
      }
    })
    const secilen = kalan.splice(bestIndex, 1)[0]!
    stops.push({
      servisId,
      durakNo: durakNo++,
      employeeId: secilen.id,
      adSoyad: secilen.adSoyad,
      lat: secilen.lat,
      lon: secilen.lon,
      oncekiDuraktanKm: Number(bestKm.toFixed(2)),
    })
    current = { lat: secilen.lat, lon: secilen.lon }
  }

  stops.push({
    servisId,
    durakNo,
    employeeId: null,
    adSoyad: OFIS.ad,
    lat: OFIS.lat,
    lon: OFIS.lon,
    oncekiDuraktanKm: Number(haversineKm(current, OFIS).toFixed(2)),
  })
  return stops
}

function buildService(servisId: number): Service {
  const route = buildRoute(servisId)
  const toplamKm = route.reduce((sum, s) => sum + s.oncekiDuraktanKm, 0)
  const tahminiSureDk = Math.round((toplamKm / ORTALAMA_HIZ_KMH) * 60)
  // Ofise 08:30 varışına göre geriye sayılan kalkış saati.
  const varis = 8 * 60 + 30
  const kalkis = Math.max(0, varis - tahminiSureDk)
  const saat = String(Math.floor(kalkis / 60)).padStart(2, '0')
  const dakika = String(kalkis % 60).padStart(2, '0')

  return {
    id: servisId,
    kisiSayisi: employees.filter((e) => e.servisId === servisId).length,
    minKapasite: MIN_KAPASITE,
    maxKapasite: MAX_KAPASITE,
    toplamKm: Number(toplamKm.toFixed(1)),
    tahminiSureDk,
    kalkisSaati: `${saat}:${dakika}`,
  }
}

/** Yeni kişiyi, kapasitesi dolmamış servisler arasında en yakın olana atar. */
function assignService(konum: { lat: number; lon: number }): number {
  let bestId = 1
  let bestKm = Infinity
  for (let servisId = 1; servisId <= SERVIS_SAYISI; servisId++) {
    const kisi = employees.filter((e) => e.servisId === servisId)
    if (kisi.length >= MAX_KAPASITE) continue
    const km = kisi.length
      ? Math.min(...kisi.map((e) => haversineKm(konum, e)))
      : haversineKm(konum, GARAJ)
    if (km < bestKm) {
      bestKm = km
      bestId = servisId
    }
  }
  return bestId
}

function delay<T>(value: T, ms = 260): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms))
}

/** Sahte katmanda her personel bir servise atanır; tip daralması için. */
function requireServis(servisId: number | null): number {
  if (servisId == null) throw new Error("Personel bir servise atanmamış.")
  return servisId
}

function clone<T>(value: T): T {
  return structuredClone(value)
}

export function getEmployees(): Promise<Employee[]> {
  return delay(clone(employees))
}

export function getServices(): Promise<Service[]> {
  const list = Array.from({ length: SERVIS_SAYISI }, (_, i) =>
    buildService(i + 1),
  )
  return delay(list)
}

export function getServiceRoute(id: number): Promise<RouteStop[]> {
  return delay(buildRoute(id))
}

function resultFor(
  servisId: number,
  employee: Employee | null,
): Promise<MutationResult> {
  return delay(
    {
      employee: employee ? clone(employee) : null,
      service: buildService(servisId),
      route: buildRoute(servisId),
    },
    420,
  )
}

export function createEmployee(input: EmployeeInput): Promise<MutationResult> {
  const ilce = input.ilce || pick(ILCELER).ad
  const merkez = ILCELER.find((i) => i.ad === ilce) ?? pick(ILCELER)
  const konum = {
    lat: input.lat ?? merkez.lat + rnd(-0.02, 0.02),
    lon: input.lon ?? merkez.lon + rnd(-0.02, 0.02),
  }
  const servisId = assignService(konum)
  const employee: Employee = {
    id: nextEmployeeId++,
    adSoyad: input.adSoyad,
    cinsiyet: input.cinsiyet,
    yas: input.yas,
    adres: input.adres,
    ilce,
    lat: konum.lat,
    lon: konum.lon,
    arabaliMi: input.arabaliMi,
    cocukVarMi: input.cocukVarMi,
    servisId,
  }
  employees.push(employee)
  return resultFor(servisId, employee)
}

export function updateEmployee(
  id: number,
  input: EmployeeInput,
): Promise<MutationResult> {
  const employee = employees.find((e) => e.id === id)
  if (!employee) return Promise.reject(new Error('Personel bulunamadı.'))

  employee.adSoyad = input.adSoyad
  employee.adres = input.adres
  employee.cinsiyet = input.cinsiyet
  employee.yas = input.yas
  employee.arabaliMi = input.arabaliMi
  employee.cocukVarMi = input.cocukVarMi
  if (input.ilce) employee.ilce = input.ilce
  if (input.lat != null && input.lon != null) {
    employee.lat = input.lat
    employee.lon = input.lon
  }
  return resultFor(requireServis(employee.servisId), employee)
}

export function deleteEmployee(id: number): Promise<MutationResult> {
  const index = employees.findIndex((e) => e.id === id)
  if (index === -1) return Promise.reject(new Error('Personel bulunamadı.'))
  const silinen = employees.splice(index, 1)[0]!
  return resultFor(requireServis(silinen.servisId), null)
}

export function getTrafficSnapshot(
  bucket: TrafficBucket,
): Promise<TrafficSnapshot> {
  // Akşam zirvesi sabahtan biraz daha yoğun; her çağrıda hafifçe oynar.
  const taban = bucket === 'sabah' ? 68 : 74
  return delay({
    bucket,
    yogunlukYuzde: Math.round(taban + rnd(-9, 9)),
    guncellemeZamani: new Date().toISOString(),
  })
}
