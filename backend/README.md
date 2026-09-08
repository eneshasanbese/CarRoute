# CarRoute — Backend

Spring Boot 4.1 · Java 21 · PostgreSQL. Personel servis rota sistemi API'si.

## Çalıştırma

```bash
./mvnw spring-boot:run
```

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

**Maliyet.** Bacaklar mesafeyle değil **süreyle** ölçülür: kuş uçuşu mesafe
karayolu çarpanıyla (1.35) düzeltilir ve o bölgenin sabah zirvesi ortalama hızına
bölünür. Böylece trafiği yoğun kısa bir bacak, boş uzun bir bacaktan pahalı
olabilir.

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

## Ayarlar

Hepsi `application.properties` üzerinden değiştirilebilir (`RouteSettings`):

```properties
carroute.office.lat=40.995800
carroute.office.lon=29.206900
carroute.office.arrival=08:00
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

**İlçe kolonu yok.** Arayüzdeki `ilce` alanı adres metninden çıkarılıyor
(`AddressUtils.extractDistrict`), çünkü `worker` tablosunda ayrı bir ilçe kolonu
yok. Seed adres biçimi (`... Pendik/İstanbul`) için 12 ilçenin hepsinde doğru
çalışıyor.

**Çocuk bilgisi.** Yalnızca `has_child` boolean'ı var; çocuk sayısı tutulmuyor.

**Araçlı personel** yine de bir servise atanıyor — kendi aracı olanların servis
dışında bırakılması istenirse `AssignmentService` içinde filtrelenmeli.
