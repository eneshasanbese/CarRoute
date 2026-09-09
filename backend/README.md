# CarRoute — Backend

Spring Boot 4.1 · Java 21 · PostgreSQL. Personel servis rota sistemi API'si.

## Çalıştırma

```bash
docker compose up -d osrm   # rota motoru — bkz. "Gerçek yol rotası (OSRM)"
./mvnw spring-boot:run
```

OSRM ayakta değilse uygulama yine çalışır; rota kuş uçuşu tahmine düşer ve
harita duraklar arasını düz çizgiyle bağlar.

Uygulama açılışında iki adım çalışır (`@Order` ile sıralı):

1. `TrafficDataSeeder` — `traffic_density_202501.csv` dosyasını okuyup sabah/akşam
   zirvesi için geohash başına ağırlıklı ortalama hızı `traffic_speed` tablosuna
   yazar. Tablo doluysa atlar.
2. `RouteBootstrapRunner` — `service_vehicle_id` alanı NULL olan işçileri
   servislere dağıtır. Atanmamış kimse yoksa hiçbir şey yapmaz.

## API

| Yöntem   | Yol                                    | Açıklama                                          |
| -------- | -------------------------------------- | ------------------------------------------------- |
| `GET`    | `/api/employees`                       | Bütün personel                                     |
| `POST`   | `/api/employees`                       | Ekler, servise atar, rotayı yeniden hesaplar        |
| `PUT`    | `/api/employees/{id}`                  | Günceller; adres değiştiyse servisi yeniden seçer   |
| `DELETE` | `/api/employees/{id}`                  | Siler, ilgili servisin rotasını yeniden hesaplar    |
| `GET`    | `/api/services`                        | 10 servisin doluluk ve rota özeti                   |
| `GET`    | `/api/services/{id}`                   | Tek servisin özeti                                  |
| `GET`    | `/api/services/{id}/route`             | Sıralı duraklar (şoför evi → işçiler → ofis)        |
| `POST`   | `/api/services/reassign`               | Bütün atamaları sıfırlayıp baştan dağıtır           |
| `GET`    | `/api/traffic/snapshot?bucket=sabah`   | `sabah` \| `aksam` yoğunluk özeti                   |

Ekleme/güncelleme/silme yanıtları `{ employee, service, route }` döner: etkilenen
servisin güncel hali ve yeniden hesaplanmış rotası. Arayüz React Query cache'ini
doğrudan bu yanıtla günceller.

Hatalar düz JSON: `{ status, error, message }` — 400 (geçersiz gövde/parametre),
404 (kayıt yok), 409 (kapasite dolu, kısıt ihlali), 500.

## Rota mantığı

**Başlangıç ve bitiş.** Sabit garaj yok. Her servis kendi şoförünün ev adresinden
kalkar, atanmış işçileri toplar ve ofiste biter
(Sancaktepe, `40.9958 / 29.2069`). Kalkış saati ofiste 08:00'de olunacak şekilde
geriye sayılır.

**İki farklı maliyet var, bilerek.** _Sıralama ve atama_ kuş uçuşu mesafenin
karayolu çarpanıyla (1.35) düzeltilmiş halini kullanır; atama sırasında binlerce
kez hesaplandığı için ucuz olması şart. _Raporlanan_ mesafe, süre ve haritaya
çizilen çizgi ise OSRM'den gelen gerçek yol verisidir — servis başına tek bir
istek. Süre her iki durumda da yol mesafesinin o koridorun sabah zirvesi ortalama
hızına bölünmesiyle bulunur, yani trafik verisi OSRM ile birlikte de kullanılır.

**Trafik hızı** (`TrafficSpeedService`). Koordinat geohash-6 hücresine çevrilip
`traffic_speed` tablosunda aranır. İBB veri seti ana arterleri ölçtüğü için her
konut mahallesinin hücresi tabloda yok; bu yüzden kademeli geri çekilme var:
geohash-6 → geohash-5 → geohash-4 → o zaman dilimi için şehir ortalaması →
sabit varsayılan hız. İndeks bellekte tutulur.

**Atama** (`AssignmentService`), üç aşama:

1. _Kurulum_ — pişmanlık sıralı açgözlü atama: en yakın iki servis arasındaki
   farkı büyük olan işçi önce yerleşir. Gerçek kapasite yerine yumuşak bir tavan
   (ortalama + 2) uygulanır; aksi halde şoförleri aynı ilçede toplanmış
   servislerden biri herkesi topluyor, diğeri boş kalıyor.
2. _Asgari doluluk onarımı_ — 5 kişinin altında kalan servise, fazlası olan
   servislerden en uygun yolcu taşınır.
3. _İyileştirme_ — sınırlı yerel arama: her işçi en yakın 4 alternatif servise
   taşınmayı dener, iki servisin toplam süresi azalıyorsa taşınır. Kapasite
   tavanı (15) ve asgari doluluk (5) korunur.

**Sıralama** (`RouteService`) — en-yakın-komşu ile kurulup 2-opt ile iyileştirilir;
şoför evi ve ofis sabit uçlardır. Durak sırası veritabanında tutulmaz, her istekte
yeniden hesaplanır (servis başına en fazla 15 kişi olduğu için milisaniyeler sürer).

Asgari 5 kişi kuralı yalnızca **dağıtım** aşamasında hedeflenir. Sonradan personel
silinip bir servis 5'in altına düşerse servisler birleştirilmez; arayüz yalnızca
uyarı rozeti gösterir.

## Gerçek yol rotası (OSRM)

Duraklar arasını düz çizgiyle birleştirmek yerine gerçek yolu takip etmek için
bir rota motoru gerekir. Burada **OSRM** (Open Source Routing Machine) kendi
makinemizde Docker'da çalışıyor: ücretsiz, API key yok, istek sınırı yok.

`GET /api/services/{id}/route` yanıtındaki `geometry` alanı bu motordan gelir —
yolu takip eden `[lat, lon]` noktaları. Aynı istekten her bacağın gerçek yol
mesafesi de alınır, `oncekiDuraktanKm` ve `toplamKm` bu değerlerdir.

**Kurulum** (bir kez; ~500 MB indirir, işlerken birkaç GB disk ve RAM ister):

```bash
bash scripts/prepare-osrm.sh                      # extract + partition + customize
docker compose --env-file osrm/osrm.env up -d osrm
curl "http://localhost:5000/route/v1/driving/29.05,40.99;29.20,41.01?overview=false"
```

Elinizde hazır bir OSRM imajı varsa `OSRM_IMAGE` ile verin. **Hazırlık ve
çalıştırma aynı imajla yapılmalı**: OSRM'in ürettiği `.osrm` dosya biçimi sürüme
bağlıdır, farklı sürümle hazırlanan veriyi `osrm-routed` "unsupported file
version" diyerek reddeder.

```bash
OSRM_IMAGE=osrm/osrm-backend:v5.22.0 bash scripts/prepare-osrm.sh
OSRM_IMAGE=osrm/osrm-backend:v5.22.0 docker compose up -d osrm
```

**Neden Türkiye eksraktı, İstanbul şehir eksraktı değil?** BBBike'ın İstanbul
kutusu `40.90–41.27 lat / 28.66–29.27 lon`. Verideki işçiler ise `40.83–41.13` ve
`29.03–29.32` aralığında; yani Tuzla/Pendik'in doğusu ve güneyi kutunun dışında
kalıyor. Kesilen bölgedeki adresler için OSRM ya rota bulamaz ya da en yakın
kenara yapıştırıp yanlış rota üretir — bu yüzden 645 MB'lık Türkiye eksraktı
kullanılıyor.

Motor kapalıysa `OsrmClient` bir kez uyarır ve kuş uçuşu hesaba döner; API
`geometry: null` gönderir, arayüz de durakları düz çizgiyle bağlar. Yani OSRM
zorunlu değil, sadece rotayı gerçekçi yapıyor.

Denemelik olarak `carroute.osrm.base-url=https://router.project-osrm.org`
verilebilir (OSRM'in herkese açık demo sunucusu, kurulum gerektirmez) ama bu
sunucu istek sınırlıdır ve kalıcı kullanım için uygun değildir.

Sonraki adım olarak OSRM'in `/table` servisi atama algoritmasına da bağlanabilir;
o zaman sıralama da kuş uçuşu yerine gerçek yol süreleriyle yapılır.

## Ayarlar

Hepsi `application.properties` üzerinden değiştirilebilir (`RouteSettings`):

```properties
carroute.office.lat=41.010412
carroute.office.lon=29.204878
carroute.office.arrival=08:00
carroute.osrm.enabled=true
carroute.osrm.base-url=http://localhost:5000
carroute.osrm.timeout-ms=8000
carroute.capacity.min=5
carroute.route.road-factor=1.35
carroute.route.boarding-minutes=1.0
carroute.route.fallback-speed=30.0
carroute.traffic.free-flow-speed=80.0
carroute.cors.allowed-origins=http://localhost:5173,http://localhost:5174
```

## Bilinen veri konuları

**Yaş alanı boş.** Arayüz yaş gösterdiği için `Worker` entity'sine `age` kolonu
eklendi, ama seed verisinde yaş yok — mevcut 100 satırda NULL, API `0` döndürür.
Doldurmak için:

```sql
UPDATE worker SET age = 22 + floor(random() * 36)::int WHERE age IS NULL;
```

**Identity sequence.** Seed dosyası id'leri açıkça yazdığı için sonundaki `setval`
satırlarının da çalışması şart; yoksa ilk `POST /api/employees` isteği
`duplicate key value violates unique constraint "worker_pkey"` ile 409 döner.
Onarmak için:

```sql
SELECT setval(pg_get_serial_sequence('worker','id'), (SELECT MAX(id) FROM worker), true);
SELECT setval(pg_get_serial_sequence('driver','id'), (SELECT MAX(id) FROM driver), true);
SELECT setval(pg_get_serial_sequence('service_vehicle','id'), (SELECT MAX(id) FROM service_vehicle), true);
```

**Ofis koordinatı düzeltildi.** Seed dosyasındaki `40.9958 / 29.2069` çifti
adresle uyuşmuyor: o nokta Eyüp Sultan Mahallesi'ne, yani adreste yazan Meclis
Mahallesi'nin 1.6 km güneyine düşüyor (Nominatim ile doğrulandı). Varsayılan
artık Meclis Mahallesi merkezi: `41.010412 / 29.204878`. Binanın tam noktası
biliniyorsa `carroute.office.lat/lon` ile verilmeli.

**İlçe kolonu yok.** Arayüzdeki `ilce` alanı adres metninden çıkarılıyor
(`AddressUtils.extractDistrict`), çünkü `worker` tablosunda ayrı bir ilçe kolonu
yok. Seed adres biçimi (`... Pendik/İstanbul`) için 12 ilçenin hepsinde doğru
çalışıyor.

**Çocuk bilgisi.** Yalnızca `has_child` boolean'ı var; çocuk sayısı tutulmuyor.

**Araçlı personel** yine de bir servise atanıyor — kendi aracı olanların servis
dışında bırakılması istenirse `AssignmentService` içinde filtrelenmeli.
