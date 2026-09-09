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

**Sıralama gerçek yol mesafesiyle yapılır.** Durak sırasına karar veren
2-opt, "5. kişiyle 9. kişinin yerini değiştirsem ne olur" sorusunu binlerce kez
sorar; yani henüz denenmemiş bacakların maliyetini bilmesi gerekir. Bu yüzden
servis başına bir kez OSRM'in `/table` servisinden **N×N yol mesafesi matrisi**
alınır ve bütün sıralama o matris üzerinden yapılır. Aynı bilgiyi `/route` ile
toplamak N² ayrı istek ederdi.

Bu, kuş uçuşu × 1.35 tahmininin yapamadığı şeyi yapar: vadinin iki yakasındaki
iki adres kuş uçuşu 1.8 km görünüp yoldan 7 km olabiliyor, eski hesap onları
"komşu" sanıp arka arkaya sıralıyordu.

**Matris simetrik değildir ve bu sıralama algoritmasını değiştirir.** Tek yön ve
köprü çıkışları yüzünden A→B ile B→A farklı çıkar (ölçülen bir örnekte 10.76 km
/ 22.47 km). İki sonucu var:

1. 2-opt'un "ters çevrilen parçanın iç maliyeti değişmez" kısayolu geçersiz —
   kaldırıldı, turun tamamı hesaplanıyor. En fazla 15 durak olduğu için ucuz.
2. 2-opt tek başına yetmiyor. Bir parçayı ters çevirmek asimetrik maliyetle
   içindeki her bacağı pahalı yöne çevirebiliyor, arama yerel optimumda
   kilitleniyor: 6 duraklı bir serviste 50.73 km bulup duruyordu, gerçek optimum
   44.64 km'ydi. Bu yüzden **Or-opt** eklendi — 1-3 duraklık parçayı *yönünü
   bozmadan* başka bir yere taşır. Aynı serviste optimali buluyor.

Maliyetler düğüm çifti başına bir kez hesaplanıp tabloya alınır (en fazla 17×17);
yerel arama yüz binlerce kez sorduğu için her seferinde geohash kodlamak
aramanın kendisinden pahalıya geliyordu.

**Kuş uçuşu tahmin hâlâ duruyor**, iki yerde: toplu dağıtımda (100 işçi × 10
servis × birkaç tur = yüz binlerce değerlendirme, ağ isteği kaldırmaz) ve OSRM
ulaşılamadığında her yerde.

**Süre**, yol mesafesinin o koridorun sabah zirvesi ortalama hızına bölünmesiyle
bulunur — mesafe OSRM'den, hız İBB verisinden. OSRM'in kendi `duration` değeri
bilerek kullanılmıyor: o boş yolun süresidir, İstanbul sabahını bilmez.

**Süre, OSRM'in süresi üzerine tıkanıklık çarpanı uygulanarak bulunur.**

```
süre = OSRM_süresi × tıkanıklık_çarpanı
çarpan(hücre) = max(1, gece_hızı / zirve_hızı)
```

Her kaynak yapabildiği işte kullanılıyor: OSRM yol sınıfını, hız limitini ve
dönüş cezalarını bilir; İBB verisi yalnızca "bu bölge kendi normalinden ne kadar
yavaş" sorusuna cevap verir. Her hücre **kendi** gece hızına normalize edildiği
için ara sokak, otoyolun hızını değil kendi tıkanıklığını miras alır. Bunun
neden gerekli olduğu ve nereye kadar yettiği için bkz.
"Modelin bilinen sınırları".

Serbest akış referansı `traffic_speed` tablosundaki üçüncü dilimden gelir:
`SERBEST_AKIS` (01:00–05:00). Bu dilim yüklü değilse çarpan 1 döner ve sistem
aşağıdaki hız tabanlı hesaba iner.

**Hız, rota çizgisi boyunca örneklenir.** Bir bacak ortalama 7 geohash
hücresinden geçiyor; bacağın yalnızca iki ucuna bakmak bunların 2'sini görüyordu.
Ölçülen hata bacak başına medyan %11, en kötü %44'tü ve S1'de servis toplamında
18 dakika ediyordu — yani kullanıcıya söylenen kalkış saati 18 dakika yanlıştı.
Toplamda hata küçük görünüyordu çünkü fazla ve eksik tahminler birbirini
götürüyordu; servis bazında görünüyordu.

Artık `/route`'tan gelen çizgi parça parça yürünüyor, her parçanın orta noktası
kendi hücresinin hızıyla değerlendirilip mesafe-ağırlıklı harmonik ortalama
alınıyor. **Ek OSRM isteği yok** — zaten elimizdeki geometri kullanılıyor.
Mesafe yine `/route` bacaklarından geliyor; geometriden yalnızca *hız profili*
çıkarılıyor, böylece raporlanan km ile süre aynı kaynaktan tutarlı kalıyor.
Geometri yoksa (OSRM kapalı) eski iki uçlu hesaba düşülür.

Bilinen sınır: sıralamayı yapan optimizasyon hâlâ iki uçlu hızı kullanıyor,
çünkü henüz denenmemiş sıralar için geometri yok. Yani rapor kesin, sıralama
kararı yaklaşık. Mesafe sıralamada baskın olduğu için bu fark ikincil.

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

Motorun iki servisi kullanılıyor ve ikisi farklı soruya cevap veriyor:

| Servis   | Ne sorar                                  | Ne zaman                                  |
| -------- | ----------------------------------------- | ----------------------------------------- |
| `/table` | "bu noktalar arası bütün mesafeler nedir" | sıralamadan önce, servis başına 1 istek   |
| `/route` | "bu sıradaki noktaları yoldan bağla"      | sıra kesinleştikten sonra, 1 istek        |

`GET /api/services/{id}/route` yanıtındaki `geometry` alanı `/route`'tan gelir —
yolu takip eden `[lat, lon]` noktaları. `oncekiDuraktanKm` ve `toplamKm` da aynı
istekteki bacak mesafeleridir.

`/route` çağrısı `continue_straight=false` ile yapılır: servis kapıda durup
yolcu aldığı için ara durakta U dönüşü gerçekçi, ayrıca `/table` her çifti
bağımsız en kısa yol olarak ölçtüğü için varsayılan davranış optimizasyonun
modeliyle raporlanan rotayı ayrıştırıyordu (10 servislik ölçümde ~1.7 km/servis
fark).

**Çalıştırma.** Hazır İstanbul eksraktı `orm-data/` altındaysa (~370 MB, depoya
dahil değil) ek bir hazırlık gerekmez:

```bash
docker compose up -d osrm
curl "http://localhost:5000/route/v1/driving/29.05,40.99;29.20,41.01?overview=false"
```

Compose varsayılanları bu veriye göre: `orm-data/istanbul.osrm`, **CH**
algoritması, `--max-table-size 2000`.

**Algoritma ile veri eşleşmek zorunda.** `.osrm` dosyaları hangi boru hattıyla
üretildiyse sadece o algoritmayla açılır: `osrm-contract` çıktısı (`*.osrm.hsgr`)
→ `ch`, `osrm-partition` + `osrm-customize` çıktısı (`*.osrm.partition`,
`*.osrm.cells`) → `mld`. Yanlış eşleştirirseniz konteyner
"Required files are missing" deyip sonsuz yeniden başlar.

**Sıfırdan hazırlamak** (Türkiye eksraktı; ~500 MB indirir, işlerken birkaç GB
disk ve RAM ister). Script **MLD** boru hattını kullanır, dolayısıyla algoritmayı
da geçmek gerekir:

```bash
bash scripts/prepare-osrm.sh                      # extract + partition + customize
OSRM_DATA_DIR=./osrm/data OSRM_DATASET=turkey-latest OSRM_ALGORITHM=mld \
  docker compose up -d osrm
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

`carroute.osrm.table-enabled=false` verilirse sıralama kuş uçuşu tahmine döner
ama çizim `/route` ile gerçek yoldan yapılmaya devam eder — iki yaklaşımı aynı
derlemeyle kıyaslamak için kullanışlı.

**Önbellek.** Arayüz 5 saniyede bir bütün servisleri sorguluyor; her poll'da 10
servis × 2 OSRM isteği anlamsız olurdu. `RouteService` hesaplanmış rotaları
bellekte tutar. Anahtar girdinin tamamını (servis, şoför konumu, işçilerin id ve
koordinatları) kapsar; atama ya da adres değişince anahtar da değişir, yani
bayat sonuç dönmesi mümkün değil.

## Ayarlar

Hepsi `application.properties` üzerinden değiştirilebilir (`RouteSettings`):

```properties
carroute.office.lat=41.010412
carroute.office.lon=29.204878
carroute.office.arrival=08:00
carroute.osrm.enabled=true
carroute.osrm.table-enabled=true
carroute.osrm.base-url=http://localhost:5000
carroute.osrm.timeout-ms=8000
carroute.capacity.min=5
carroute.route.road-factor=1.35
carroute.route.boarding-minutes=1.0
carroute.route.fallback-speed=30.0
carroute.traffic.free-flow-speed=80.0
carroute.cors.allowed-origins=http://localhost:5173,http://localhost:5174
```

## Modelin bilinen sınırları

Süre tahmininin nereye kadar güvenilir olduğu, ölçülerek çıkarıldı. Sistemin
hiç yanılmadığını iddia etmiyoruz; nerede ve ne kadar yanıldığını biliyoruz.

**Kök sebep: İBB veri seti konumla anahtarlanmış, yolla değil.** Ölçümler
geohash-6 hücrelerine (~1.2 × 0.6 km) toplanmış. Ama hız bir *yolun* özelliği,
bir *karenin* değil. Aynı hücrenin içinde hem TEM hem ara sokak var ve tabloda
tek bir sayı duruyor. Bu, aşağıdaki sınırların hepsinin kaynağı.

**Çözülen: yanlış yola yanlış hız atanması.** Süre artık OSRM'in kendi
süresinin üzerine kuruluyor, İBB verisi yalnızca tıkanıklık çarpanı üretiyor
(bkz. `TrafficSpeedService.congestionFactor`). OSRM ara sokağın ara sokak
olduğunu bildiği için, bir sokağa otoyol hızı atanması artık matematiksel olarak
mümkün değil. Çarpan 1'in altına inemediğinden süre serbest akışın altına da
düşemez. Önceki modelde 10 servisin 3'ü boş yoldan hızlı süre üretiyordu; şimdi
0.

**Kalan 1 — hücre içi yol karışımı.** Çarpan hücre başına tek sayı; aynı
hücredeki otoyol ile ara sokak aynı oranı paylaşıyor. Hata sınırlı (çarpan
aralığı ~1.0–1.4) ve yönü güvenli tarafta: ara sokakları fazla cezalandırıp
erken varış tahmin ediyoruz. **Bu veri setiyle çözülemez.**

**Kalan 2 — ölçülmemiş bölgeler.** Adreslerin ~%18'inin geohash-6 hücresi
tabloda yok; rota uzunluğu bazında bazı servislerde bu oran %40'a çıkıyor. O
hücrelerin çarpanı komşu arterlerden türetiliyor. Etkisi toplam sürede ~%5
(önceki modelde aynı bölgeler 2–3 kat hızlı sayılıyordu).

**Kalan 3 — rota seçimi trafiğe duyarlı değil.** Çarpan süreyi düzeltiyor, ama
OSRM hâlâ boş yola göre en hızlı güzergâhı seçiyor; gerçek şoför tıkalı
otoyoldan kaçardı. Ölçüldü: OSRM'in alternatif sunduğu 4 bacaktan 1'inde
seçilen güzergâh zirvede en iyi değildi, kayıp 2.1 dakika. Bu bir alt sınır.
Tek gerçek çözümü `osrm-customize --segment-speed-file` ile hızları motorun
içine enjekte etmek; o zaman rota seçimi de değişir. İBB verisinde OSM yol
kimliği olmadığı için hücrelerin yollara eşlenmesi gerekir — ayrı bir iş.

**Kalan 4 — zaman çözünürlüğü ve tazelik.** Sabah zirvesi 06:00–08:00 aralığının
tek ortalaması, ama tur o iki saati kat ediyor. Veri Ocak 2025 statik dosyası;
gün, hava, kaza yok.

**Doğrulama notu.** Gerçek yolculuk süresi verimiz yok, dolayısıyla model
gerçeğe karşı doğrulanamıyor — yapılabilen iç tutarlılık ve fiziksel makullük
testleri. İki bağımsız kaynak (OSRM'in profil süresi ve İBB'nin gece ölçümü)
verinin bol olduğu servislerde %3–6 içinde birbirini tutuyor, seyrek olduğu
yerde ayrışıyor; bu da tabanın OSRM'den alınması kararının gerekçesi.

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
