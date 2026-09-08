-- =====================================================================
-- CarRoute - Başlangıç verisi (İstanbul Anadolu Yakası)
-- ÜRETİLMİŞ DOSYA - elle düzenleme, kaynağı: backend/tools/gen-seed.js
--   node backend/tools/gen-seed.js > backend/src/main/resources/db/seed_data.sql
--
-- Kapsam : 10 servis aracı, 10 şoför, 100 işçi
-- Not    : worker.service_vehicle_id NULL bırakıldı - hangi işçinin hangi
--          servise bineceğine rota optimizasyon algoritması karar verecek.
--
-- OFİS - varış noktası, herkes 08:00'de burada olmalı.
-- Henüz entity'si yok, rota kodunda sabit olarak kullan:
--   Meclis Mah. Atatürk Cad. C Blok Apt. No:41/K, 34785 Sancaktepe/İstanbul
--   lat 40.995800, lon 29.206900
--
-- BAŞLANGIÇ NOKTALARI - sabit garaj yok:
--   Her servis kendi şoförünün ev adresinden yola çıkar.
--   Rota = driver.latitude/longitude -> işçi durakları -> ofis (08:00'de).
--
-- ADRESLER NEDEN RASTGELE:
--   Ne işçi ne şoför adresleri servis güzergâhına, ofise ya da birbirine
--   göre yerleştirildi. Adresleri 'uygun' noktalara koymak rota problemini
--   baştan çözmüş olurdu. Tek yönlendirme ilçe başına kişi sayısı; ilçe
--   içinde mahalle, sokak ve koordinat tamamen rastgele.
--   Üretilen her koordinat iki filtreden geçti: Marmara/Boğaz kıyısının
--   suya düşen tarafı ve TEM / D-100 / Şile Otoyolu güzergâhının 250 m
--   yakını elendi. Yani her adres kapısının önünden alınabilecek bir yerde.
--   Bu filtreler yaklaşıktır (elle çıkarılmış polyline'lar, gerçek yol ağı
--   verisi değil); kesin doğrulama için adresleri bir geocoder'a sormak gerekir.
--
-- İŞÇİ DAĞILIMI - TÜİK 2023 ilçe nüfuslarına oranlandı:
--   Pendik        14 kişi
--   Ümraniye      13 kişi
--   Maltepe        9 kişi
--   Üsküdar        9 kişi
--   Sancaktepe     9 kişi
--   Kartal         9 kişi
--   Kadıköy        8 kişi
--   Ataşehir       8 kişi
--   Sultanbeyli    6 kişi
--   Çekmeköy       5 kişi
--   Tuzla          5 kişi
--   Beykoz         5 kişi
--   TOPLAM       100 kişi
--   Adalar (araçla ulaşılamıyor) ve Şile (çok uzak) kapsam dışı.
--
-- Oranlar: arabası olan 25/100 · çocuklu 40/100 · ERKEK 52 / KADIN 48
--
-- TRAFİK VERİSİ KAPSAMI:
--   Adreslerin bir kısmının geohash-6 hücresi traffic_speed tablosunda yok.
--   Sebep veri hatası değil: İBB veri seti ana arterleri ölçüyor, her konut
--   mahallesini değil. Rota kodunda hücre bulunamazsa bir fallback gerekir
--   (komşu geohash hücresi, ya da o zaman dilimi için şehir ortalaması).
--
-- Çalıştırma (tablolar Hibernate tarafından oluşturulmuş olmalı):
--   psql -U postgres -d carRoute -f backend/src/main/resources/db/seed_data.sql
-- =====================================================================

BEGIN;

-- Tekrar çalıştırılabilmesi için önce mevcut veriyi temizle.
-- worker ve driver, service_vehicle'a FK ile bağlı olduğu için sıra önemli.
DELETE FROM worker;
DELETE FROM driver;
DELETE FROM service_vehicle;

-- ---------------------------------------------------------------------
-- SERVİS ARAÇLARI - 10 adet, kapasite 15 (rota kuralı: en az 5 kişi)
-- ---------------------------------------------------------------------
INSERT INTO service_vehicle (id, plate_number, model, capacity) VALUES
  (1, '34 SRV 101', 'Mercedes-Benz Sprinter 516 CDI', 15),
  (2, '34 SRV 102', 'Ford Transit 460E', 15),
  (3, '34 SRV 103', 'Volkswagen Crafter 50', 15),
  (4, '34 SRV 104', 'Iveco Daily 65C17', 15),
  (5, '34 SRV 105', 'Mercedes-Benz Sprinter 519 CDI', 15),
  (6, '34 SRV 106', 'Ford Transit 410L', 15),
  (7, '34 SRV 107', 'Isuzu Novo Ultra', 15),
  (8, '34 SRV 108', 'Otokar Sultan Mini', 15),
  (9, '34 SRV 109', 'Volkswagen Crafter 35', 15),
  (10, '34 SRV 110', 'Iveco Daily 70C18', 15);

-- ---------------------------------------------------------------------
-- ŞOFÖRLER - 10 adet, her servise bir şoför.
-- Bu ev adresleri aynı zamanda servislerin rota başlangıç noktalarıdır.
-- Konumlar rastgele: hiçbiri işçi kümelerine göre seçilmedi.
-- ---------------------------------------------------------------------
INSERT INTO driver (id, name, surname, phone, address, latitude, longitude, service_vehicle_id) VALUES
  (1, 'Mert', 'Duman', '0541 210 96 56', 'Fikirtepe Mah. Rıdvan Paşa Cad. No:172 D:1 Kadıköy/İstanbul', 40.992098, 29.05033, 1),
  (2, 'Kaan', 'Şahin', '0555 296 72 99', 'Caferağa Mah. Hürriyet Sok. No:24 D:18 Kadıköy/İstanbul', 40.989053, 29.024473, 2),
  (3, 'Gökhan', 'Çetin', '0555 851 75 73', 'Esenyalı Mah. Güneş Sok. No:131 D:8 Pendik/İstanbul', 40.869646, 29.266848, 3),
  (4, 'Kader', 'Tunç', '0551 810 66 61', 'Esentepe Mah. Cumhuriyet Sok. No:163 D:7 Kartal/İstanbul', 40.910345, 29.213785, 4),
  (5, 'Tarık', 'Polat', '0533 414 10 39', 'Soğanlık Yeni Mah. Manolya Sok. No:41 D:1 Kartal/İstanbul', 40.917916, 29.175873, 5),
  (6, 'Volkan', 'Güneş', '0551 435 53 44', 'Yakacık Yeni Mah. Toros Sok. No:4 D:2 Kartal/İstanbul', 40.907219, 29.216159, 6),
  (7, 'Cihan', 'Yaman', '0537 855 76 18', 'Yenidoğan Mah. Güven Sok. No:175 D:19 Sancaktepe/İstanbul', 41.030526, 29.237353, 7),
  (8, 'Yavuz', 'Gündoğdu', '0542 420 17 49', 'Esenkent Mah. Zeytin Sok. No:61 D:13 Maltepe/İstanbul', 40.93903, 29.157646, 8),
  (9, 'Yeliz', 'Ateş', '0506 286 51 26', 'Çavuşbaşı Mah. Paşabahçe Cad. No:109 D:14 Beykoz/İstanbul', 41.1007, 29.113342, 9),
  (10, 'Murat', 'Gül', '0551 599 55 15', 'Veysel Karani Mah. Kavak Sok. No:82 D:6 Sancaktepe/İstanbul', 41.020856, 29.211585, 10);

-- ---------------------------------------------------------------------
-- İŞÇİLER - 100 adet ev adresi. İlçe kırılımı yukarıdaki tabloda.
-- ---------------------------------------------------------------------
INSERT INTO worker (id, name, surname, phone, address, latitude, longitude, has_car, has_child, gender, service_vehicle_id) VALUES
  -- Pendik
  (1, 'Fatih', 'Keskin', '0532 757 32 24', 'Velibaba Mah. Adalet Sok. No:125 D:9 Pendik/İstanbul', 40.907801, 29.239077, false, false, 'ERKEK', NULL),
  (2, 'Furkan', 'Fidan', '0551 667 90 16', 'Esenyalı Mah. Çınar Sok. No:68 D:19 Pendik/İstanbul', 40.870511, 29.267078, false, false, 'ERKEK', NULL),
  (3, 'Caner', 'Kurt', '0553 758 56 50', 'Kavakpınar Mah. Akasya Sok. No:34 D:3 Pendik/İstanbul', 40.886977, 29.225381, false, false, 'ERKEK', NULL),
  (4, 'Gökhan', 'Türk', '0555 558 56 69', 'Ertuğrul Gazi Mah. Mevlana Sok. No:46 D:15 Pendik/İstanbul', 40.88498, 29.264458, false, false, 'ERKEK', NULL),
  (5, 'Taner', 'Acar', '0537 856 13 31', 'Sülüntepe Mah. Karanfil Sok. No:167 D:6 Pendik/İstanbul', 40.923645, 29.255034, false, true, 'ERKEK', NULL),
  (6, 'Cem', 'Yıldırım', '0506 769 87 78', 'Kaynarca Mah. Menekşe Sok. No:133 D:14 Pendik/İstanbul', 40.902886, 29.209917, false, false, 'ERKEK', NULL),
  (7, 'Merve', 'Erdoğan', '0536 399 88 23', 'Doğu Mah. Toros Sok. No:114 D:17 Pendik/İstanbul', 40.877597, 29.24408, true, true, 'KADIN', NULL),
  (8, 'Vildan', 'Taş', '0543 477 27 99', 'Doğu Mah. Yıldız Sok. No:41 D:2 Pendik/İstanbul', 40.873983, 29.245278, true, false, 'KADIN', NULL),
  (9, 'Sevim', 'Sezer', '0551 499 53 43', 'Harmandere Mah. Zambak Sok. No:4 D:1 Pendik/İstanbul', 40.914756, 29.283702, true, false, 'KADIN', NULL),
  (10, 'Gürkan', 'Aksoy', '0505 641 86 25', 'Harmandere Mah. Dostluk Sok. No:98 D:1 Pendik/İstanbul', 40.914601, 29.290634, false, false, 'ERKEK', NULL),
  (11, 'Seda', 'Varol', '0553 805 56 90', 'Yenişehir Mah. Nergis Sok. No:112 D:6 Pendik/İstanbul', 40.904635, 29.3115, false, true, 'KADIN', NULL),
  (12, 'Buse', 'Fidan', '0543 394 86 85', 'Ertuğrul Gazi Mah. Pınar Sok. No:113 D:8 Pendik/İstanbul', 40.887745, 29.264234, false, false, 'KADIN', NULL),
  (13, 'Şule', 'Çetin', '0541 368 88 65', 'Esenyalı Mah. Ihlamur Sok. No:124 D:16 Pendik/İstanbul', 40.870347, 29.266752, false, false, 'KADIN', NULL),
  (14, 'Meryem', 'Aslan', '0553 768 83 52', 'Ertuğrul Gazi Mah. Doğu Cad. No:91 D:16 Pendik/İstanbul', 40.884562, 29.264929, false, true, 'KADIN', NULL),
  -- Ümraniye
  (15, 'Handan', 'Yavuz', '0533 370 67 75', 'Ihlamurkuyu Mah. Çınar Sok. No:174 D:8 Ümraniye/İstanbul', 41.03683, 29.14436, false, false, 'KADIN', NULL),
  (16, 'Yıldız', 'Işık', '0505 592 23 30', 'Mehmet Akif Mah. Gül Sok. No:79 D:8 Ümraniye/İstanbul', 41.018692, 29.141964, false, false, 'KADIN', NULL),
  (17, 'Kader', 'Demir', '0533 593 58 75', 'Armağanevler Mah. Atatürk Cad. No:36 D:19 Ümraniye/İstanbul', 41.024897, 29.103274, false, false, 'KADIN', NULL),
  (18, 'Enes', 'Ateş', '0533 461 79 49', 'Fatih Sultan Mehmet Mah. Muhsin Yazıcıoğlu Cad. No:142 D:9 Ümraniye/İstanbul', 41.007158, 29.115291, false, false, 'ERKEK', NULL),
  (19, 'Kübra', 'Gündoğdu', '0533 239 65 91', 'Fatih Sultan Mehmet Mah. Sakarya Sok. No:114 D:14 Ümraniye/İstanbul', 41.00656, 29.115727, true, true, 'KADIN', NULL),
  (20, 'Levent', 'Sağlam', '0543 777 82 10', 'Yamanevler Mah. Yunus Emre Sok. No:61 D:6 Ümraniye/İstanbul', 41.016054, 29.109912, true, false, 'ERKEK', NULL),
  (21, 'Melis', 'Uçar', '0544 381 34 64', 'Fatih Sultan Mehmet Mah. Umut Sok. No:23 D:16 Ümraniye/İstanbul', 41.006654, 29.115331, true, false, 'KADIN', NULL),
  (22, 'Yusuf', 'Bozkurt', '0505 431 74 93', 'Atakent Mah. Toros Sok. No:81 D:20 Ümraniye/İstanbul', 41.031427, 29.098351, false, false, 'ERKEK', NULL),
  (23, 'Kadir', 'Doğan', '0544 788 27 99', 'Mehmet Akif Mah. Muhsin Yazıcıoğlu Cad. No:8 D:20 Ümraniye/İstanbul', 41.015661, 29.14609, true, true, 'ERKEK', NULL),
  (24, 'Sultan', 'Ekinci', '0553 398 24 22', 'Ihlamurkuyu Mah. Karanfil Sok. No:97 D:17 Ümraniye/İstanbul', 41.042156, 29.141428, false, false, 'KADIN', NULL),
  (25, 'Barış', 'Güler', '0551 837 46 93', 'Yukarı Dudullu Mah. Toros Sok. No:157 D:14 Ümraniye/İstanbul', 41.028261, 29.168395, false, false, 'ERKEK', NULL),
  (26, 'Şule', 'Çakmak', '0537 374 46 69', 'Armağanevler Mah. Fatih Sok. No:89 D:7 Ümraniye/İstanbul', 41.024843, 29.100083, false, true, 'KADIN', NULL),
  (27, 'Mehmet', 'Korkmaz', '0533 895 62 96', 'Atakent Mah. Menekşe Sok. No:38 D:18 Ümraniye/İstanbul', 41.032913, 29.101261, false, true, 'ERKEK', NULL),
  -- Maltepe
  (28, 'Kaan', 'Ekinci', '0542 749 77 82', 'Fındıklı Mah. Fındıklı Cad. No:46 D:4 Maltepe/İstanbul', 40.921778, 29.151101, false, true, 'ERKEK', NULL),
  (29, 'Hanife', 'Yaman', '0538 765 67 95', 'Gülensu Mah. Fındıklı Cad. No:101 D:2 Maltepe/İstanbul', 40.928796, 29.159853, true, false, 'KADIN', NULL),
  (30, 'Melis', 'Ulusoy', '0543 474 89 52', 'Başıbüyük Mah. Fındıklı Cad. No:171 D:6 Maltepe/İstanbul', 40.929198, 29.16921, true, false, 'KADIN', NULL),
  (31, 'Aleyna', 'Fidan', '0507 518 94 87', 'Altayçeşme Mah. Zeytin Sok. No:45 D:17 Maltepe/İstanbul', 40.929671, 29.130703, false, true, 'KADIN', NULL),
  (32, 'Metin', 'Özdemir', '0545 420 38 41', 'Altayçeşme Mah. Kiraz Sok. No:13 D:3 Maltepe/İstanbul', 40.930027, 29.129376, false, true, 'ERKEK', NULL),
  (33, 'Fatma', 'Çelik', '0541 347 32 65', 'Başıbüyük Mah. Yıldız Sok. No:124 D:12 Maltepe/İstanbul', 40.926868, 29.164544, false, false, 'KADIN', NULL),
  (34, 'Sena', 'Polat', '0506 874 52 83', 'Zümrütevler Mah. Akasya Sok. No:66 D:8 Maltepe/İstanbul', 40.943995, 29.148627, false, false, 'KADIN', NULL),
  (35, 'Ceren', 'Turan', '0545 543 92 86', 'Zümrütevler Mah. Fındıklı Cad. No:40 D:15 Maltepe/İstanbul', 40.947393, 29.146563, false, true, 'KADIN', NULL),
  (36, 'Caner', 'Varol', '0545 408 20 97', 'Küçükyalı Mah. Bağdat Cad. No:127 D:10 Maltepe/İstanbul', 40.945087, 29.111705, false, true, 'ERKEK', NULL),
  -- Üsküdar
  (37, 'Batuhan', 'Gül', '0544 234 62 81', 'Altunizade Mah. Güneş Sok. No:141 D:10 Üsküdar/İstanbul', 41.022316, 29.041262, true, false, 'ERKEK', NULL),
  (38, 'Feyza', 'Çakmak', '0538 491 89 83', 'Sultantepe Mah. Karanfil Sok. No:74 D:17 Üsküdar/İstanbul', 41.029906, 29.031685, false, false, 'KADIN', NULL),
  (39, 'Emre', 'Ekinci', '0551 361 75 78', 'Barbaros Mah. İcadiye Cad. No:56 D:17 Üsküdar/İstanbul', 41.015156, 29.057605, false, false, 'ERKEK', NULL),
  (40, 'Murat', 'Yavuz', '0537 237 94 90', 'Yavuztürk Mah. Papatya Sok. No:107 D:9 Üsküdar/İstanbul', 41.032828, 29.085856, false, false, 'ERKEK', NULL),
  (41, 'Sultan', 'Bulut', '0532 285 15 26', 'Sultantepe Mah. Kısıklı Cad. No:124 D:15 Üsküdar/İstanbul', 41.031969, 29.032662, false, true, 'KADIN', NULL),
  (42, 'Osman', 'Sönmez', '0537 768 15 27', 'Çengelköy Mah. Fatih Sok. No:19 D:6 Üsküdar/İstanbul', 41.051906, 29.054051, false, true, 'ERKEK', NULL),
  (43, 'Aleyna', 'Güler', '0507 645 71 20', 'Ferah Mah. Adalet Sok. No:102 D:9 Üsküdar/İstanbul', 41.023884, 29.066817, true, true, 'KADIN', NULL),
  (44, 'Rabia', 'Güneş', '0507 362 73 10', 'Bulgurlu Mah. Selami Ali Cad. No:109 D:11 Üsküdar/İstanbul', 41.017166, 29.079858, false, false, 'KADIN', NULL),
  (45, 'Metin', 'Demirci', '0536 845 96 32', 'Sultantepe Mah. Kısıklı Cad. No:125 D:6 Üsküdar/İstanbul', 41.027452, 29.028666, false, true, 'ERKEK', NULL),
  -- Sancaktepe
  (46, 'Nazlı', 'Yalçın', '0541 773 14 71', 'Merve Mah. Lale Sok. No:173 D:5 Sancaktepe/İstanbul', 41.029581, 29.232143, false, false, 'KADIN', NULL),
  (47, 'Ceren', 'Güler', '0544 424 70 80', 'Abdurrahmangazi Mah. Atatürk Cad. No:46 D:7 Sancaktepe/İstanbul', 41.014538, 29.231511, false, true, 'KADIN', NULL),
  (48, 'Yusuf', 'Tunç', '0545 301 81 28', 'Emek Mah. Fatih Cad. No:80 D:4 Sancaktepe/İstanbul', 41.016827, 29.22275, false, true, 'ERKEK', NULL),
  (49, 'Nurcan', 'Karaca', '0536 758 98 22', 'Hilal Mah. Atatürk Cad. No:89 D:11 Sancaktepe/İstanbul', 41.023993, 29.230662, false, false, 'KADIN', NULL),
  (50, 'Yıldız', 'Güler', '0551 491 13 83', 'Veysel Karani Mah. Mevlana Sok. No:109 D:11 Sancaktepe/İstanbul', 41.023216, 29.213839, true, false, 'KADIN', NULL),
  (51, 'İlknur', 'Bozkurt', '0555 791 31 72', 'Fatih Mah. Fatih Cad. No:119 D:18 Sancaktepe/İstanbul', 41.017967, 29.251689, true, true, 'KADIN', NULL),
  (52, 'Rıdvan', 'Aksoy', '0538 514 10 43', 'Emek Mah. Karanfil Sok. No:171 D:11 Sancaktepe/İstanbul', 41.019496, 29.22222, false, false, 'ERKEK', NULL),
  (53, 'Ali', 'Korkmaz', '0536 755 86 26', 'Eyüp Sultan Mah. Mevlana Sok. No:121 D:18 Sancaktepe/İstanbul', 41.000508, 29.237613, false, false, 'ERKEK', NULL),
  (54, 'Oğuz', 'Duman', '0535 364 91 58', 'Safa Mah. Atatürk Cad. No:14 D:19 Sancaktepe/İstanbul', 41.009068, 29.258442, true, true, 'ERKEK', NULL),
  -- Kartal
  (55, 'Cansu', 'Koç', '0555 866 68 10', 'Cevizli Mah. Huzur Sok. No:65 D:11 Kartal/İstanbul', 40.912942, 29.160165, true, true, 'KADIN', NULL),
  (56, 'Metin', 'Türk', '0542 286 34 95', 'Cevizli Mah. Cumhuriyet Sok. No:31 D:2 Kartal/İstanbul', 40.91377, 29.161892, false, true, 'ERKEK', NULL),
  (57, 'Berkay', 'Ateş', '0532 821 30 15', 'Atalar Mah. Karanfil Sok. No:121 D:6 Kartal/İstanbul', 40.904926, 29.179396, false, true, 'ERKEK', NULL),
  (58, 'Rukiye', 'Arslan', '0542 323 82 35', 'Uğur Mumcu Mah. Topselvi Cad. No:137 D:2 Kartal/İstanbul', 40.920105, 29.197973, true, true, 'KADIN', NULL),
  (59, 'Levent', 'Bulut', '0533 468 48 89', 'Uğur Mumcu Mah. Fatih Sok. No:130 D:3 Kartal/İstanbul', 40.920293, 29.201198, true, true, 'ERKEK', NULL),
  (60, 'Zehra', 'Kara', '0543 422 65 85', 'Yunus Mah. Cumhuriyet Sok. No:54 D:10 Kartal/İstanbul', 40.896296, 29.206805, false, false, 'KADIN', NULL),
  (61, 'Osman', 'Yalçın', '0538 666 27 97', 'Cevizli Mah. Topselvi Cad. No:146 D:19 Kartal/İstanbul', 40.915416, 29.158561, false, false, 'ERKEK', NULL),
  (62, 'Yusuf', 'Korkmaz', '0537 335 77 38', 'Hürriyet Mah. Papatya Sok. No:130 D:10 Kartal/İstanbul', 40.899961, 29.190749, true, true, 'ERKEK', NULL),
  (63, 'Erkan', 'Aydın', '0551 610 89 82', 'Topselvi Mah. Zeytin Sok. No:173 D:3 Kartal/İstanbul', 40.909295, 29.197689, false, true, 'ERKEK', NULL),
  -- Kadıköy
  (64, 'Uğur', 'Kaya', '0538 583 22 98', 'Bostancı Mah. Güneş Sok. No:137 D:13 Kadıköy/İstanbul', 40.95309, 29.091678, false, false, 'ERKEK', NULL),
  (65, 'Batuhan', 'Kılıç', '0544 806 80 90', 'Erenköy Mah. Güven Sok. No:78 D:5 Kadıköy/İstanbul', 40.967568, 29.071274, false, false, 'ERKEK', NULL),
  (66, 'Şerife', 'Polat', '0537 296 39 88', 'Hasanpaşa Mah. Pınar Sok. No:3 D:5 Kadıköy/İstanbul', 40.990779, 29.045699, false, false, 'KADIN', NULL),
  (67, 'Yasemin', 'Kılınç', '0545 253 67 42', 'Suadiye Mah. Dere Sok. No:50 D:8 Kadıköy/İstanbul', 40.960942, 29.079632, false, false, 'KADIN', NULL),
  (68, 'Caner', 'Şimşek', '0507 824 72 28', 'Göztepe Mah. Ihlamur Sok. No:145 D:6 Kadıköy/İstanbul', 40.977259, 29.065398, true, false, 'ERKEK', NULL),
  (69, 'Koray', 'Yalçın', '0555 314 32 32', 'Merdivenköy Mah. Gül Sok. No:147 D:17 Kadıköy/İstanbul', 40.987185, 29.070424, false, true, 'ERKEK', NULL),
  (70, 'Volkan', 'Yalçın', '0545 390 11 15', 'Sahrayıcedit Mah. Şemsettin Günaltay Cad. No:156 D:1 Kadıköy/İstanbul', 40.977003, 29.086911, false, true, 'ERKEK', NULL),
  (71, 'Sevgi', 'Tunç', '0533 432 17 40', 'Caddebostan Mah. Şemsettin Günaltay Cad. No:9 D:4 Kadıköy/İstanbul', 40.96871, 29.064912, false, false, 'KADIN', NULL),
  -- Ataşehir
  (72, 'Sultan', 'Şimşek', '0535 668 60 66', 'Örnek Mah. Güven Sok. No:151 D:4 Ataşehir/İstanbul', 40.996709, 29.108752, false, false, 'KADIN', NULL),
  (73, 'Mehmet', 'Keskin', '0533 432 28 33', 'İnönü Mah. Sümbül Sok. No:1 D:13 Ataşehir/İstanbul', 40.995421, 29.096053, true, true, 'ERKEK', NULL),
  (74, 'Serkan', 'Yalçın', '0551 645 87 23', 'İnönü Mah. Mevlana Sok. No:10 D:1 Ataşehir/İstanbul', 40.992329, 29.089803, false, false, 'ERKEK', NULL),
  (75, 'Murat', 'Öztürk', '0543 431 99 16', 'İnönü Mah. Vedat Günyol Cad. No:130 D:10 Ataşehir/İstanbul', 40.996159, 29.095213, false, false, 'ERKEK', NULL),
  (76, 'Vildan', 'Turan', '0533 683 74 76', 'Yeni Sahra Mah. Uludağ Sok. No:153 D:5 Ataşehir/İstanbul', 40.98046, 29.096335, true, false, 'KADIN', NULL),
  (77, 'Tolga', 'Avcı', '0551 386 53 27', 'Mustafa Kemal Mah. Nergis Sok. No:8 D:19 Ataşehir/İstanbul', 40.988488, 29.10924, false, true, 'ERKEK', NULL),
  (78, 'Merve', 'Acar', '0533 388 26 42', 'Yeni Sahra Mah. Menekşe Sok. No:114 D:2 Ataşehir/İstanbul', 40.986218, 29.095403, false, false, 'KADIN', NULL),
  (79, 'Özlem', 'Doğan', '0535 232 87 36', 'İçerenköy Mah. Karanfil Sok. No:2 D:5 Ataşehir/İstanbul', 40.975875, 29.110425, true, true, 'KADIN', NULL),
  -- Sultanbeyli
  (80, 'Vildan', 'Keskin', '0505 821 52 25', 'Ahmet Yesevi Mah. Battalgazi Cad. No:116 D:4 Sultanbeyli/İstanbul', 40.97559, 29.265254, false, false, 'KADIN', NULL),
  (81, 'Emre', 'Varol', '0507 783 22 27', 'Mimar Sinan Mah. Akasya Sok. No:102 D:5 Sultanbeyli/İstanbul', 40.972568, 29.285821, false, false, 'ERKEK', NULL),
  (82, 'Cem', 'Çakmak', '0542 850 33 79', 'Mecidiye Mah. Zambak Sok. No:156 D:6 Sultanbeyli/İstanbul', 40.971161, 29.255934, false, false, 'ERKEK', NULL),
  (83, 'Şahin', 'Yıldız', '0538 301 90 29', 'Ahmet Yesevi Mah. Yasemin Sok. No:110 D:12 Sultanbeyli/İstanbul', 40.973329, 29.25801, false, true, 'ERKEK', NULL),
  (84, 'Emre', 'Çakır', '0551 522 54 55', 'Hasanpaşa Mah. Hasanpaşa Cad. No:12 D:6 Sultanbeyli/İstanbul', 40.960243, 29.265484, false, true, 'ERKEK', NULL),
  (85, 'Zeynep', 'Duman', '0535 765 78 39', 'Mecidiye Mah. Çınar Sok. No:39 D:2 Sultanbeyli/İstanbul', 40.973191, 29.250022, false, true, 'KADIN', NULL),
  -- Çekmeköy
  (86, 'Selin', 'Duman', '0533 688 54 16', 'Ekşioğlu Mah. Umut Sok. No:89 D:19 Çekmeköy/İstanbul', 41.031093, 29.176432, false, false, 'KADIN', NULL),
  (87, 'Koray', 'Yıldız', '0532 800 16 93', 'Nişantepe Mah. Bahar Sok. No:131 D:8 Çekmeköy/İstanbul', 41.058696, 29.231487, false, true, 'ERKEK', NULL),
  (88, 'Onur', 'Yalçın', '0506 463 70 40', 'Aydınlar Mah. Adalet Sok. No:104 D:15 Çekmeköy/İstanbul', 41.038591, 29.192863, true, false, 'ERKEK', NULL),
  (89, 'Cem', 'Bulut', '0542 269 93 61', 'Hamidiye Mah. Uludağ Sok. No:6 D:15 Çekmeköy/İstanbul', 41.047546, 29.20396, false, false, 'ERKEK', NULL),
  (90, 'Cansu', 'Kılınç', '0555 472 43 37', 'Taşdelen Mah. Cumhuriyet Cad. No:44 D:11 Çekmeköy/İstanbul', 41.029337, 29.226404, false, false, 'KADIN', NULL),
  -- Tuzla
  (91, 'Efe', 'Koç', '0535 667 94 39', 'Evliya Çelebi Mah. İstasyon Cad. No:45 D:16 Tuzla/İstanbul', 40.840813, 29.30658, false, true, 'ERKEK', NULL),
  (92, 'Ebru', 'Varol', '0506 611 32 86', 'Yayla Mah. Uludağ Sok. No:109 D:5 Tuzla/İstanbul', 40.859405, 29.318114, false, true, 'KADIN', NULL),
  (93, 'Tarık', 'Taş', '0544 595 51 70', 'Evliya Çelebi Mah. Cumhuriyet Sok. No:110 D:10 Tuzla/İstanbul', 40.838592, 29.306502, false, false, 'ERKEK', NULL),
  (94, 'Fatih', 'Kılınç', '0536 262 71 98', 'İçmeler Mah. Adalet Sok. No:30 D:9 Tuzla/İstanbul', 40.850399, 29.297586, false, true, 'ERKEK', NULL),
  (95, 'Levent', 'Öztürk', '0507 520 42 57', 'Mimar Sinan Mah. Defne Sok. No:2 D:9 Tuzla/İstanbul', 40.82644, 29.320778, false, false, 'ERKEK', NULL),
  -- Beykoz
  (96, 'Hanife', 'Özdemir', '0538 326 22 55', 'Çubuklu Mah. Karanfil Sok. No:11 D:12 Beykoz/İstanbul', 41.086882, 29.069332, true, false, 'KADIN', NULL),
  (97, 'Kübra', 'Uçar', '0507 807 48 85', 'Baklacı Mah. Cami Sok. No:121 D:12 Beykoz/İstanbul', 41.117941, 29.08937, true, false, 'KADIN', NULL),
  (98, 'Taner', 'Güler', '0538 756 90 20', 'Rüzgarlıbahçe Mah. Kavacık Cad. No:120 D:17 Beykoz/İstanbul', 41.099187, 29.095758, false, false, 'ERKEK', NULL),
  (99, 'Nurcan', 'Yıldırım', '0551 858 59 95', 'Ortaçeşme Mah. Fatih Cad. No:162 D:7 Beykoz/İstanbul', 41.126229, 29.095749, false, false, 'KADIN', NULL),
  (100, 'Şerife', 'Kılınç', '0543 395 36 45', 'Baklacı Mah. Güneş Sok. No:86 D:19 Beykoz/İstanbul', 41.116701, 29.08853, false, false, 'KADIN', NULL);

-- ---------------------------------------------------------------------
-- Id sayaçlarını ilerlet - aksi halde Hibernate'in eklediği ilk kayıt
-- id=1 ile çakışıp duplicate key hatası verir.
-- pg_get_serial_sequence kullanıldı: Hibernate kolonu ister IDENTITY
-- ister bigserial olarak üretmiş olsun ikisinde de çalışır.
-- ---------------------------------------------------------------------
SELECT setval(pg_get_serial_sequence('service_vehicle', 'id'), 10, true);
SELECT setval(pg_get_serial_sequence('driver', 'id'), 10, true);
SELECT setval(pg_get_serial_sequence('worker', 'id'), 100, true);

COMMIT;

-- Kontrol sorguları:
--   SELECT COUNT(*) FROM worker;                        -- 100 dönmeli
--   SELECT COUNT(*) FROM driver;                        -- 10
--   SELECT COUNT(*) FROM service_vehicle;               -- 10
--   SELECT gender, COUNT(*) FROM worker GROUP BY gender;
--   SELECT has_car, COUNT(*) FROM worker GROUP BY has_car;
--   SELECT substring(address from '([^ ]+)/İstanbul') AS ilce, COUNT(*)
--     FROM worker GROUP BY 1 ORDER BY 2 DESC;
