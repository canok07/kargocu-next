# Parcelrise Tycoon 0.2.0 — son APK doğrulaması

3 Ekim 2026. Önce ekonomi dengelendi (M15), ardından kariyer ve içerik genişletildi (M16). Sekiz kademe, yedi bölge, 14 merkez, 22 rota, kiralık başlangıç dahil dokuz araç türü, sekiz yük kategorisi. Marka Parcelrise Tycoon ve özgün paket/yükseliş amblemi kullanılır. Uygulama kimliği `com.canok.kargotycoon`, versionCode 3. Çalışma yerelde, CC ve alt agent olmadan tamamlandı.

## Kontroller

- 90 normal motor testi, 20 ağır stres testi ve iki çoklu kariyer testi geçti. İki kariyer testi 15 geçerli komut kariyerini içerir. Başlangıç para/araç erişimi, uzman araç çeşitliliği, ileri oyun erişilebilirliği, alternatif rota etkisi, pazar büyüme sınırı, gerçek eski save ve eski aktif faturaların devamı doğrulandı.
- Görünür Pixel_10a/Android17/API37 emülatöründe 37 Android testi: 0 hata, başarısız veya atlanan. Kayıt/kurtarma, eşzamanlı komutlar, başarısız yazma, çift dokunma, ayarlar, gerçek kazançla araç/şoför/atama/iki paralel iş, yeni haritanın son şehir ve çeşitlilik koşulu dahil. XML `validation/M16-Android-37-Passed.xml`.
- Debug, test ve küçültülmüş release derlemesi başarılı. Lint 0 hata / mevcut 1 OldTargetApi uyarısı. Son test-kare-saati düzeltmesinden sonra yalnız Android test APK'si yeniden derlendi; üretim APK'si değişmedi.
- Son imzalı release üzerinde ilk iş €104,28 ödeme / €27,12 azami rezerv önizlemesiyle kabul edildi. Tamamlanmadan gerçek süreç kapatılıp açıldı; iş korundu. Tamamlanınca bakiye €193,84 oldu; yeniden süreç kapatma/açmada aynı kaldı. Rezervin kullanılmayan kısmı iade edildi; tek net ödeme uygulandı.
- Son release, 100× koşusunun gerçek kariyer kaydını aynı kimlik/imzayla açtı; kademe 8, büyümüş filo ve yedi açık bölge korundu. Normal hızda kalan teslimatlar tamamlandı.

## Görünür 100× endgame ölçümü

Opt-in `Endgame100xTest`, seed 20261003, 100 EUR/kiralık araç başlangıcı. Kriter **en yüksek şirket kademesi ve tüm bölgelerin açılmasıdır**; her araçtan satın almak veya oyunun sona ermesi değildir. Üretim hız ayarı değiştirilmez. Gerçek geçen süre × 60 × 100 ile oyun saati ilerler. AdvanceDay, para/araç enjeksiyonu veya farklı katalog kullanılmaz. Geçerli pozitif marj/süre işleri seçilir; 200 EUR tamponla farklı araç alınır, şoför işe alınıp atanır ve servis yapılır.

| Değer | Sonuç |
| --- | --- |
| Ölçülen gerçek süre | 447.339 ms = **7 dk 27,339 sn** |
| Normal saat karşılığı | 44.646.200 ms = **12 sa 24 dk 6,2 sn** |
| Oyun günü / şirket kademesi | 32 / 8 |
| Tamamlanan / benzersiz ödenen iş | 405 / 405 |
| Sahip olunan araç / farklı tür / şoför | 6 / 6 / 6 |
| Açık bölge / en çok paralel iş | 7 / 7 |
| Son kullanılabilir bakiye | 1.475.600 cent = €14.756,00 |
| Kaydedilen geçerli komut | 2.562 |

Instrumentasyon **OK (1 test)** verdi. Sonuç, gün bazlı ilerleme ve gerçek final save `validation/endgame100x-v020/` altında bulunur. Testin kare saati de her turda ilerletildi; canlı şirket ekranı görünürdü. İlk görsel olarak donuk koşu bilinçli iptal edildi ve başarı yerine sayılmadı; ayrıntı [M16 ölçüm notlarında](M16-Uzun-Kariyer.md).

Beş ayrı hızlı motor kariyeri normal/gün-atlama yollarını karşılaştırdı: normal saat 12 sa 24 dk, 405–423 iş; gün atlayınca yalnız teslimat beklemeleri 83 dk 36 sn–95 dk 08 sn. Bu ikinci sayı karar/menü/dokunma süresini içermez. **İnsan oynama süresi garantisi değildir.** İlk araç 8–9 işte erişilir. Önceki dört kademeli sürümün yaklaşık 3,6 saatlik saat karşılığı gün atlamayı dışlıyordu; genişletme bu yüzden yalnız bekleme yerine iş, yatırım ve içerik ekledi.

## APK ve mağaza hazırlığı

`ParcelriseTycoon-0.2.0-20261003.apk`, 1.679.013 byte; SHA-256 `42BF9DAC1B06F17FACA73C99F74E39122733E20EF76FA67BF284B6DF8864AA10`. R8/resource shrinking release, mevcut geliştirme anahtarıyla imzalandı; v2/v3 imza doğrulaması geçti.

`store/google-play` iki dilde metin, 16 gerçek 1080×1920 telefon ekranı, 512×512 RGBA ikon ve iki 1024×500 RGB tanıtım görselini içerir. Ekranlar son normal hızlı release APK'den alınır; gelişmiş kariyer gerçek 100× testinden gelir. Görsellerde yalnız alpha kanalı kaldırılır, oyun pikselleri değiştirilmez. Düzenlenebilir vektörler, kaynak APK/hash ve erişilebilir açıklamalar bulunur. Telefonun 1080×2424 doğal ölçüsü, font1.0 ve özgün rotation ayarları çekimlerden sonra geri alınır.

Fiziksel cihaz veya gerçek 60 dakikalık görünür soak testi yapılmadı. Google Play yayını yapılmadı. Kalıcı üretim yükleme anahtarı ve imzalı AAB, destek e-postası ve Console beyanları ilerideki yayın hazırlığıdır. Bugünkü APK geliştirme imzası taşır.
