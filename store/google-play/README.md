# Parcelrise Tycoon — mağaza hazırlığı

Bu klasör Google Play için metin ve görsel hazırlık paketidir. Mağazaya yükleme veya yayın yapılmadı. İçerik 0.2.0 / versionCode 3 sürümünü anlatır. Android application ID `com.canok.kargotycoon` ve kayıt biçimi korunmuştur; görünen ad her dilde **Parcelrise Tycoon** olur.

**Güncel yayın teslimi: 0.2.1 / versionCode 4.** Oyun içi gizlilik bilgisi ve özel yükleme anahtarıyla imzalanabilen AAB desteği eklendi; ekonomi/kayıt motoru değişmedi. Arkadaşına teslim ve Console adımları [publication/ONCE-BUNU-OKU.md](publication/ONCE-BUNU-OKU.md) içindedir. Görseller gerçek 0.2.0 sürümünden alınmıştır; yeni gizlilik penceresi haricinde aynı oyunu gösterir. `release.json` ve `assets.json` çekim kaynağına ait 0.2.0 bilgilerini korur; yayın paketinin 0.2.1 bilgileri ayrı `verification.json` dosyasında yer alır.

## İçerik

- `en-US/` ve `tr-TR/`: başlık, kısa/tam açıklama, bu sürümdeki değişiklikler ve gerçek telefon ekranları.
- `art/icon-512.png`: APK'deki özgün paket/yükseliş ambleminin 512×512 PNG çıktısı.
- `art/feature-en-US-1024x500.png`, `art/feature-tr-TR-1024x500.png`: iki dilde 1024×500 tanıtım görseli.
- `art/*.svg`: düzenlenebilir vektör kaynakları; ikon doğrudan Android vektöründen üretilir.
- `assets.json`: ölçüler, SHA-256 değerleri, TR/EN erişilebilir açıklamalar ve çekim kaynağı.

Telefon görüntüleri görünür Pixel_10a/API37 emülatöründe, teslim edilen imzalı ve küçültülmüş release APK'den alınmıştır. Görüntüler 1080×1920'dir; cihaz çerçevesi veya sonradan eklenmiş pazarlama metni içermez. Yalnız PNG alpha kanalı kaldırılır; oyun pikselleri değiştirilmez. Başlangıç ve geçerli oyun komutlarıyla büyüyen kariyer gösterilir, para/araç/kayıt enjeksiyonu yapılmaz. Gelişmiş kariyer ayrı 100× testinde elde edilip normal hızlı release APK'de açılmıştır. Tanıtım görselleri markayı anlatan vektör tasarımlardır; oynanış ekranı değildir.

## Daha sonra Play Console'da

1. Başlık ve açıklamaları seçtiğin dile göre ilgili alanlara kopyala. İkonu, o dile ait tanıtım görselini ve telefon ekranlarını yükle. Erişilebilir açıklamalar `assets.json` içinde bulunur.
2. Kendine ait kalıcı destek e-posta adresini ekle. Gerekli gizlilik politikası, veri güvenliği, içerik derecelendirmesi, hedef kitle, reklam ve diğer uygulama beyanlarını son yayın sürümüne göre tamamla.
3. Yayın için Android App Bundle oluştur, kalıcı yükleme anahtarını güvenli tut ve Play App Signing'i yapılandır. Teslim edilen APK geliştirme anahtarıyla imzalıdır; üretim yükleme anahtarı bu depoda bulunmaz.
4. Fiziksel cihazlarda ve desteklenen Android sürümlerinde doğrulama yap. Hesabın için Console'un istediği test/yayın koşullarını tamamla. Sonradan eklenen SDK, reklam, ağ veya ödeme özellikleri için açıklama ve beyanları tekrar değerlendir.

## Kaynak ve yeniden üretim

`tools/export-store-art.cjs`, Node.js ve sharp kullanarak vektörlerden aynı boyutlu PNG'leri üretir. Bu araç Android uygulamasının çalışma bağımlılığı değildir. `tools/check-store-kit.cjs` metin uzunluklarını, görsel biçimlerini ve ölçüleri doğrular; `assets.json` üretir.

Başlık 30, kısa açıklama 80, tam açıklama 4000 karakter sınırına göre kontrol edilir. İkon 512×512 ve RGBA; tanıtım görselleri 1024×500, ekranlar 1080×1920 ve RGB PNG'dir. Her dilde sekiz telefon ekranı vardır.

Google'ın 3 Ekim 2026'da kontrol edilen resmi kaynakları: [Uygulama oluşturma ve mağaza metinleri](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [Önizleme görselleri ve gereksinimleri](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).
