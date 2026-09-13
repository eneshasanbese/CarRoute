# Personel Servis Rota Sistemi — Frontend

Admin/ops paneli: 10 sabit servisin doluluğu, personel yönetimi ve rota haritaları.
Backend ile iletişim **Redux Toolkit + Axios** üzerinden yürür.

## Çalıştırma

```bash
npm install
cp .env.example .env   # ilk kurulumda
npm run dev            # http://localhost:5173
npm run build          # tsc -b + vite build
npm run lint           # oxlint
```

Backend'in ayakta olması gerekir (`../backend`, varsayılan `http://localhost:8080`).

## Ortam değişkenleri (`.env`)

| Değişken                  | Açıklama                                                       |
| ------------------------- | -------------------------------------------------------------- |
| `VITE_API_BASE_URL`       | Backend adresi (varsayılan `http://localhost:8080`)             |
| `VITE_GEOCODING_PROVIDER` | `nominatim` (key gerektirmez) veya `none` (autocomplete kapalı) |
| `VITE_GEOCODING_API_KEY`  | Nominatim dışı bir sağlayıcıya geçilirse                        |

`.env` git'e girmez (yerel ayar). `.env.example` onun şablonu — depoya giren,
"hangi değişkenler var" sorusunun cevabı olan dosya. Yeni bir makinede
`cp .env.example .env` ile başlanır.

## Yapı

```
src/
  api/                      # Axios katmanı — backend'e giden tek kapı
    axiosClient.ts          #   axios örneği + apiErrorMessage (okunabilir hata metni)
    employeesApi.ts         #   /api/employees
    driversApi.ts           #   /api/drivers
    servicesApi.ts          #   /api/services
    trafficApi.ts           #   /api/traffic
  store/                    # Redux Toolkit
    index.ts                #   configureStore + RootState / AppDispatch
    hooks.ts                #   useAppDispatch / useAppSelector (tipli)
    employeesSlice.ts       #   fetch / create / update / delete thunk'ları
    driversSlice.ts         #   şoförler için aynı dört thunk
    servicesSlice.ts        #   servisler + servis id -> rota
    trafficSlice.ts         #   sabah / akşam yoğunluk
    uiSlice.ts              #   harita katman ve vurgu durumu
    selectors.ts            #   özet ve türetilmiş veriler (createSelector)
  components/
    layout/AppLayout.tsx    # üst nav: Dashboard / Personel / Şoförler / Harita
    common/                 # ErrorState ("Tekrar dene"), EmptyState, TableParts (ortak tablo parçaları)
    employees/              # EmployeeTable, EmployeeFormModal, EmployeeDetailModal, AddressAutocomplete
    drivers/                # DriverTable, DriverFormModal, DriverDetailModal
    services/               # ServiceCard, CapacityGauge
    map/                    # RouteMap, RouteStopList
    traffic/                # TrafficPanel
    ui/                     # shadcn tarzı temel bileşenler (Radix + Tailwind)
  pages/                    # Dashboard, PersonelYonetimi, SoforYonetimi, ServisDetay, HaritaGenel
  hooks/                    # usePolling (periyodik tazeleme), useStablePagination
  lib/                      # capacity, serviceColors, geocode, tableFilters, utils
  types.ts                  # paylaşılan tipler
```

### Veri akışı

Bileşen `useAppSelector` ile store'dan okur, `useAppDispatch` ile thunk çalıştırır.
Thunk `api/` katmanındaki Axios fonksiyonunu çağırır; hata olursa
`apiErrorMessage` ile okunabilir tek bir metne çevirip `rejectWithValue` ile
slice'a yazar. Her slice `status` (`idle | loading | succeeded | failed`) ve
`error` tutar; sayfalar iskelet / hata / boş durumlarını bu ikisine bakarak
gösterir.

**Mutasyon sonrası tazeleme.** Backend ekleme/güncelleme/silme yanıtında
`{ employee, service, route }` döndürüyor. `servicesSlice`, `employeesSlice`'ın
thunk'larını `isAnyOf(...)` ile dinleyip etkilenen servisi ve rotasını doğrudan
store'a yazar — ayrıca bir GET isteği atılmaz. İki slice arasındaki bu bağ
[src/store/servicesSlice.ts](src/store/servicesSlice.ts) sonundaki `addMatcher`
bloğunda.

**Polling.** Sayfalar [usePolling](src/hooks/usePolling.ts) ile 5 sn'de bir
tazeler (trafik paneli 60 sn). WebSocket'e geçilirse bu hook yerine soket
olayları aynı thunk'ları dispatch etmeli. Tablolar tazelemede bulunduğu sayfada
kalır ([useStablePagination](src/hooks/useStablePagination.ts)); ilk sayfaya
yalnızca arama, filtre ya da sıralama değişince dönülür.

**Toast.** Bildirimler slice'larda değil, dispatch eden bileşende:
`await dispatch(...).unwrap()` başarılıysa `toast.success`, hata fırlatırsa
`toast.error`. Böylece slice'lar saf kalıyor.

### Renk kuralları

- Kapasite: yeşil (rahat) / sarı (12-14) / kırmızı (15, dolu) / mavi rozet
  (5 altı, "Min. kapasite altında"). Tek kaynak:
  [src/lib/capacity.ts](src/lib/capacity.ts).
- Servis renkleri o an kayıtlı servislerin id sırasına göre dağıtılır; aynı anda
  var olan iki servis aynı renge düşmez (10 renklik palet, sonrası koyu tonlar).
  Bileşenler rengi `useServiceColor()` ile alır:
  [src/lib/serviceColors.ts](src/lib/serviceColors.ts).

### Servis adı

Servis her yerde aracın plakasıyla anılır, id ile değil: id silme ve eklemelerle
boşluklu ilerlediği için 10 servis varken "Servis-12" görünüyordu. Id yalnızca
URL'de (`/servis/:id`) ve renk eşlemesinde kalır. Tek kaynak:
[src/lib/serviceName.ts](src/lib/serviceName.ts); yalnızca servis id'si taşıyan
kayıtlar adı `selectServiceNames` ile okur.

### Harita çizgisi

Rota çizgisi backend'in gönderdiği `geometry` alanından çizilir — OSRM'in
ürettiği, yolu takip eden `[lat, lon]` noktaları. Backend'de OSRM kapalıysa bu
alan `null` gelir ve [RouteMap](src/components/map/RouteMap.tsx) durakları düz
çizgiyle birleştirmeye geri döner.

### Harita soyutlaması

`RouteMap` dışarıya yalnızca `RouteStop` ile konuşur; Leaflet tipleri prop'lara
sızmaz. Mapbox GL JS'e geçilmek istenirse sadece bu dosyanın gövdesi değişir.

## Spec'ten sapmalar

- **Redux Toolkit + Axios** kullanıldı (spec TanStack Query ve Zustand diyordu).
  İstemci tarafı UI state'i de ayrı bir kütüphane yerine `uiSlice`'ta duruyor;
  tek bir state kütüphanesi var.
- **React 19** kullanıldı (spec React 18 diyordu) — mevcut Vite scaffold'u zaten
  React 19 ile geliyordu; shadcn/Radix bileşenleri `forwardRef` yerine ref-prop
  stiliyle yazıldı.
- **TanStack Table v8.21** sabitlendi. v9 stabil olarak yayında ama store tabanlı
  yeni bir mimariye geçmiş durumda; v8 API'si öngörülebilir olduğu için tercih edildi.
- **POST/PUT gövdesi** spec'teki alanlara ek olarak `adSoyad` (tabloda ve formda
  zorunlu, kontratta unutulmuş görünüyor) ve autocomplete'ten seçilen
  `ilce`/`lat`/`lon` alanlarını taşır. Kullanıcı listeden seçim yapmadıysa
  koordinat gönderilmez; backend adresteki ilçeye göre konum tahmin eder.
- **`cocukSayisi` alanı yok.** Spec `Employee` tipinde çocuk sayısı istiyordu;
  sistemde çocuk bilgisi yalnızca var/yok olarak tutuluyor.
- **`servisId` null olabilir.** Backend bir personeli henüz hiçbir servise
  atamamışsa tabloda "Atanmadı" görünür.
- `shadcn` CLI ile init edilmedi; bileşenler doğrudan `src/components/ui/` altına
  yazıldı (CLI'ın yaptığı da budur).

## Kapsam dışı

- **Auth yok.** Spec'te MVP dışında bırakıldı; giriş ekranı eklenmedi.
- Servis oluşturma/silme arayüzü yok — servis sayısı sabit (10).
- Durak sırası salt-okunur; sıralamayı tamamen backend algoritması belirler.
