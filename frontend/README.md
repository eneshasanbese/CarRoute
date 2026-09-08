# Personel Servis Rota Sistemi — Frontend

Admin/ops paneli: 10 sabit servisin doluluğu, personel yönetimi ve rota haritaları.

## Çalıştırma

```bash
npm install
cp .env.example .env   # ilk kurulumda
npm run dev            # http://localhost:5173
npm run build          # tsc -b + vite build
npm run lint           # oxlint
```

## Ortam değişkenleri (`.env`)

| Değişken                  | Açıklama                                                            |
| ------------------------- | ------------------------------------------------------------------- |
| `VITE_API_BASE_URL`       | Backend adresi (varsayılan `http://localhost:8080`)                  |
| `VITE_USE_MOCK`           | `true` iken tarayıcı içi sahte veri katmanı kullanılır (varsayılan `false`) |
| `VITE_GEOCODING_PROVIDER` | `nominatim` (key gerektirmez) veya `none` (autocomplete kapalı)      |
| `VITE_GEOCODING_API_KEY`  | Nominatim dışı bir sağlayıcıya geçilirse                             |

### Sahte veri katmanı

Arayüz varsayılan olarak gerçek backend'e (`../backend`, port 8080) bağlanır.
Backend çalışmıyorken `.env` içinde `VITE_USE_MOCK=true` yapılırsa
[src/lib/mockApi.ts](src/lib/mockApi.ts) devreye girer: tarayıcı içinde 10 servis
ve ~90 personel üretir, rota sırasını en-yakın-komşu ile hesaplar, ekleme/silmede
etkilenen servisi yeniden hesaplar.

Mock katmanını [src/lib/api.ts](src/lib/api.ts) dışında hiçbir dosya tanımıyor.
Header'daki "Demo verisi" rozeti mock açıkken görünür.

## Yapı

```
src/
  components/
    AppLayout.tsx            # üst nav: Dashboard / Personel / Harita
    ServiceCard.tsx          # dashboard kartı (+ ServiceCardSkeleton)
    CapacityGauge.tsx        # 0..15 bar, min. kapasite (5) çizgisi
    EmployeeTable.tsx        # TanStack Table: sıralama, filtre, sayfalama
    EmployeeFormModal.tsx    # RHF + Zod, adres autocomplete içerir
    AddressAutocomplete.tsx  # debounce'lu adres arama, koordinat gizli
    RouteMap.tsx             # react-leaflet sarmalayıcı, tek/çoklu rota
    RouteStopList.tsx        # salt-okunur durak sırası
    TrafficPanel.tsx         # sabah / akşam trafik yoğunluğu
    states.tsx               # ErrorState ("Tekrar dene") + EmptyState
    ui/                      # shadcn tarzı bileşenler (Radix + Tailwind)
  pages/                     # Dashboard, PersonelYonetimi, ServisDetay, HaritaGenel
  hooks/                     # useEmployees, useServices, useServiceRoute, useTraffic
  lib/                       # api, mockApi, geocode, capacity, serviceColors, queryKeys
  store/uiStore.ts           # zustand: harita katman/vurgu durumu
  types.ts                   # paylaşılan tipler
```

### Veri akışı

- Tüm server state React Query'de. `services`, `employees` ve rota sorguları
  5 sn'de bir polling yapar ([src/lib/queryKeys.ts](src/lib/queryKeys.ts) →
  `POLL_INTERVAL_MS`). WebSocket'e geçilirse bu sabit kaldırılıp soket olayları
  `invalidateQueries` tetiklemeli.
- Ekleme/güncelleme/silme yanıtındaki `{ service, route }` doğrudan cache'e
  yazılır, ardından `employees` + `services` invalidate edilir — kart, tablo ve
  harita aynı anda tazelenir.
- Sonuç kullanıcıya `sonner` toast'ı ile bildirilir
  (ör. "Ayşe Kaya silindi — Servis-4 rotası güncellendi").

### Renk kuralları

- Kapasite: yeşil (rahat) / sarı (12-14) / kırmızı (15, dolu) / mavi rozet
  (5 altı, "Min. kapasite altında"). Tek kaynak:
  [src/lib/capacity.ts](src/lib/capacity.ts).
- Servis renkleri servis id'sine sabitlenmiştir (10 ayırt edilebilir renk):
  [src/lib/serviceColors.ts](src/lib/serviceColors.ts).

### Harita soyutlaması

`RouteMap` dışarıya yalnızca `RouteStop` ile konuşur; Leaflet tipleri prop'lara
sızmaz. Mapbox GL JS'e geçilmek istenirse sadece bu dosyanın gövdesi değişir.

## Spec'ten sapmalar

- **React 19** kullanıldı (spec React 18 diyordu) — mevcut Vite scaffold'u zaten
  React 19 ile geliyordu; shadcn/Radix bileşenleri `forwardRef` yerine ref-prop
  stiliyle yazıldı.
- **TanStack Table v8.21** sabitlendi. v9 stabil olarak yayında ama store tabanlı
  yeni bir mimariye geçmiş durumda; v8 API'si öngörülebilir olduğu için tercih edildi.
- **POST/PUT gövdesi** spec'teki alanlara ek olarak `adSoyad` (tabloda ve formda
  zorunlu, kontratta unutulmuş görünüyor) ve autocomplete'ten seçilen
  `ilce`/`lat`/`lon` alanlarını taşır. Kullanıcı listeden seçim yapmadıysa
  koordinat gönderilmez ve adresi backend'in geocode etmesi beklenir.
- **`cocukSayisi` alanı kaldırıldı.** Spec `Employee` tipinde çocuk sayısı ve
  formda bir sayı girdisi istiyordu; sistemde çocuk bilgisi yalnızca var/yok
  olarak tutuluyor, sayı tutulmuyor.
- **`servisId` null olabilir.** Backend bir personeli henüz hiçbir servise
  atamamışsa tabloda "Atanmadı" görünür.
- `shadcn` CLI ile init edilmedi; bileşenler doğrudan `src/components/ui/` altına
  yazıldı (CLI'ın yaptığı da budur).

## Kapsam dışı

- **Auth yok.** Spec'te MVP dışında bırakıldı; giriş ekranı eklenmedi.
- Servis oluşturma/silme arayüzü yok — servis sayısı sabit (10).
- Durak sırası salt-okunur; sıralamayı tamamen backend algoritması belirler.
