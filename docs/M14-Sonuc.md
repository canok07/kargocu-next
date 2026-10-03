# M14 — doğrulanmış 0.1.1 ara checkpoint, 3 Ekim 2026

Marka Parcelrise Tycoon olarak değişti. Paket/yükseliş vektör amblemi hem açılışta hem ana ekranlarda hem uygulama ikonunda kullanılır. Android kimliği ve save şeması korunur. Mağaza materyalleri `store/google-play` altındadır.

UI aksiyonları yalnız başarılı kayıt sonrası geri döner; işlem bekleme durumu ikinci mali komutu engeller. Onay düğmesi tutar hesaplanmadan etkinleşmez. Dil/ses/titreşim alanları son kaydedilmiş ayarlara ayrı yama olarak uygulanır. Eski bildirim kapanışı yeni bildirimi silemez. Yeni oyun navigasyonu Panel'e döner; açılış başlatma düğmesi kaydırılan metnin dışında kalır.

## Doğrulama

- 81 normal motor ve 20 ağır motor stres testi bugün yeniden çalıştırıldı; hepsi geçti.
- Marka/UI değişikliğinden sonra görünür Pixel_10a/API37 cihazında 36 Android testi: 0 başarısız, 0 hata, 0 atlanan. XML: `validation/M14-Android-36-Passed.xml`.
- Ayrı opt-in görünür 100× Android testi: 1 test geçti. Kaynak: `Endgame100xTest.kt`; sonuçlar `validation/endgame100x/`.
- Debug, Android test paketi, R8/resource shrinking release ve lint başarılı. Lint 0 hata, mevcut OldTargetApi için 1 uyarı.
- Son imzalı release APK gerçek açılış, büyük yazı 2×, dikey/yatay açılış ve TR/EN kontrollerinden geçti. Bir teslimat tamamlanmadan gerçek süreç kapatıldı; yeniden açılışta €210.04 elde edildi. Üç ek gerçek süreç kapatma/açmada bakiye aynı kaldı ve iş yeniden ödenmedi.
- Mağaza: 16 gerçek 1080×1920 ekran, 512×512 RGBA ikon, iki 1024×500 RGB tanıtım görseli; metin uzunlukları ve dosya biçimleri doğrulandı. Bütün ekranlar son release APK'den alındı. Emülatör ekran/font/rotation ayarları geri yüklendi.

## İlk 100× süre ölçümü

Kriter: mevcut en yüksek şirket kademesi 4 ve tüm 3 bölgenin açılması. Başlangıç 100 EUR/kiralık araç, seed 20261003. Gerçek zaman 100× çarpanıyla ilerletildi; AdvanceDay, para ekleme veya değiştirilmiş katalog kullanılmadı. Otomatik strateji en yüksek ödemeli geçerli işi seçer; 200 EUR işletme tamponuyla iki araç satın alır, şoför işe alır/atar, bakım ve onarım yapar.

- Ölçülen gerçek süre: 129.885 saniye (yaklaşık 2:10).
- Oyun: 10. gün, kademe 4, 3 açık bölge, 28 tamamlanmış iş, 2 sahip olunan araç, 2 şoför; tüm 28 ödeme benzersiz.
- Normal saat karşılığı: 12.967.100 ms = 3 saat 36 dakika 7.1 saniye.
- Bu tek otomatik kariyerin saat karşılığıdır, insanın zorunlu oynama süresi değildir. Günü bitirme özelliği süreyi kısaltabilir. Son kademe/tüm bölgeler dışında oyunun ayrı bir bitiş sahnesi yoktur.

Kullanıcı bu ölçümden sonra önce ekonomi dengesi, sonra kariyer genişletmesi istedi. **0.1.1 son teslim olarak sunulmayacak; M15/M16 için taban checkpoint'tir.** Son GitHub push'u yeni kapsam tamamlandıktan sonra yapılır.

APK: `ParcelriseTycoon-0.1.1-20261003.apk`, SHA-256 `D7BFA3FF83CAB36BE59EC5EE7C4EE73FD4FD5834240BC37ABCA31BED6EE621A0`, 1.662.629 byte. Geliştirme anahtarıyla imzalıdır. Fiziksel cihaz, üretim anahtarı ve gerçek 60 dakika testi yapılmadı; mağaza yayını yapılmadı.
