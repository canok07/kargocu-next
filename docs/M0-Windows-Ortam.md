# M0 — Windows ortam tespiti

Tarih: 30 Eylül 2026, Europe/Berlin. Bu belge yalnızca gözlem ve plan içerir; build/test başarısı iddiası değildir.

## Referansların korunması

- C:\Users\canok\AndroidStudioProjects\kargocu ve curier yalnız okunacak referanslar.
- İkisinde de Git deposu bulunamadı. Kaynak/config dosyalarının 72 dosyalık SHA256 manifesti work/m0-reference-20260930/source-manifest.json içinde tutuluyor.
- GDD ve kullanıcının yeni proje talimatı aynı arşive alındı. Eski kaynaklarda değişiklik yapılmadı.
- Önceki Courier Rush v0.1/v0.2 tasarımları, yol planı, 0.5.003 arıza tespiti ve oyun denetimi belgeleri bulundu.
- CC kayıtlı projeleri: courier-rush, courier-rush-05001, courier-rush-step2-20260926, courier-rush-step4-20260926, courier-rush-step5-20260926. Bunların özellik/platform karşılaştırması CC M0 görevinin kapsamında.
- Arama kapsamı: AndroidStudioProjects, ApkProjects, Downloads, Desktop, Documents/Codex ve OneDrive. Documents/Codex içindeki bazı eski geçici klasörler erişim engelli; bilgisayarın her konumu tarandı denemez.

## Gözlenen mevcut yapılandırma

kargocu: AGP 9.4.1, Gradle 9.6.0, Kotlin/Compose plugin 2.2.10, Compose BOM 2026.02.01; min/target/compile SDK 37; Java kaynak/hedef 11. Lifecycle sürümleri 2.6.1 ve 2.7.0 karışık. Firebase AI 17.+ dinamik sürüm. Bunlar öneri değil, eski projenin dosyalarında görülen değerler.

## Windows Android araçları

- Android Studio: C:\Program Files\Android\Android Studio; build AI-261.26222.65.2614.16379836.
- Studio JBR: Java 25.0.3. Sistem PATH Java: Eclipse Adoptium Java 8.0.472. Yeni build komutlarında JDK açıkça seçilecek; global Java ayarı değiştirilmeyecek.
- SDK: C:\Users\canok\AppData\Local\Android\Sdk.
- Platform klasörleri: android-34, android-35, android-36, android-37.0. API 37 paketinde CodeName boş, açıklama Android SDK Platform 17.
- Build Tools: 34.0.0, 35.0.1, 36.0.0.
- Gradle dağıtım klasörleri: 8.2-bin, 8.14.3-all, 9.6.0-bin. Yalnız klasör varlığı doğrulandı.
- adb: 37.0.1-15733141. emulator-5554 bağlı ve device durumunda. Yeni oyun henüz kurulmadı/test edilmedi.
- AVD kayıtları: Pixel_10_Pro_XL ve Pixel_10a.

## Resmî belge kontrolü

30 Eylül 2026 tarihinde erişildi. Kesin proje pinleri M0 raporu incelendikten sonra belirlenecek, uyumluluk M1 gerçek build ile doğrulanacak.

- [AGP 9.4 uyumluluğu](https://developer.android.com/build/releases/agp-9-4-0-release-notes): Gradle 9.6.0, en az JDK 17, desteklenen en yüksek API 37, varsayılan Build Tools 36.0.0. Sayfada 9.4.1 patch sürümü doğrulanamadı; eski config değeri varlık kanıtı sayılmayacak.
- [Gradle Java uyumluluğu](https://docs.gradle.org/current/userguide/compatibility.html): Java 25 ile Gradle çalıştırma desteği 9.1.0 ve sonrası. Java 8 Gradle 9 için kullanılmayacak.
- [Kotlin sürümleri](https://kotlinlang.org/docs/releases.html): 2.4.20 yayımlanmış; sırf en yeni olduğu için seçilmeyecek. AGP built-in Kotlin/Compose/serialization plugin uyumu birlikte kontrol edilecek.
- [AGP built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin): yeni Android yapılandırması bu desteği göz önüne alacak.
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom): stable örnek 2026.09.00. BOM Compose compiler sürümünü belirlemez.
- [Navigation sürümleri](https://developer.android.com/jetpack/androidx/releases/navigation): 2.10.2, Lifecycle 2.10.0 bağımlılığı; 2.10 serisinde minSdk 24. Bu veri tek başına uygulama minSdk kararı değildir; tüm seçilen bağımlılıklar kontrol edilecek.
- [Activity sürümleri](https://developer.android.com/jetpack/androidx/releases/activity).
- [Lifecycle sürümleri](https://developer.android.com/jetpack/androidx/releases/lifecycle).
- [Core sürümleri](https://developer.android.com/jetpack/androidx/releases/core).
- [Coroutines sürümleri](https://github.com/Kotlin/kotlinx.coroutines/releases): 1.11.0 sürümü Kotlin 2.2.20 ile yayımlanmış; compiler/runtime metadata uyumu kontrol edilecek.
- [Serialization sürümleri](https://github.com/Kotlin/kotlinx.serialization/releases): 1.12.0-RC kararlı sürüm sayılmayacak; seçilecek runtime compiler plugin ile doğrulanacak.

## Dur komutu

Kullanıcı “dur” dediğinde yeni iş başlatılmayacak. Aktif CC görevi mevcut bridge cancel işlemiyle iptal edilecek; sunucu kapatılmayacak. Çalışan yerel build/monitor süreçleri mümkün olan en kısa sürede kesilecek. Son tamamlanan milestone ve bekleyen işlemler kaydedilecek. Yeniden başlama kullanıcı talimatıyla olacak.

## Bu aşamada test

Dosya ve araç envanteri, adb device görünürlüğü ve resmî belge kontrolü yapıldı. Unit test, lint, APK build veya oyun smoke testi henüz çalıştırılmadı. M0 kaynak analizi sürüyor.

## Görsel referans incelemesi

Eski Courier Rush 0.5.003 TR ana ekran görüntüsü ve v0.5.0 seçili görev ekranı incelendi; görüntü kanıtı özellik implementasyon/test kanıtı sayılmadı.

- Korunacak fikirler: sıradaki teslimat/hedef, tahmini net kazanç, paket ve risk etiketleri, gider dökümü, uygun araç bilgisi, ilk adımları anlatan tutorial listesi.
- Yeni UI planı: ana ekran kasa/aktif teslimat/sıradaki hedef; görevlere ayrı ekran; araç ve şoför yönetimi ayrı akışlar; aktif iş için kısa progress ve sonuç.
- Gözlenen sorunlar: 0.5.003 alt navigasyonunda bazı ikon ve etiketler üst üste görünüyor. v0.5.0 kartındaki net kâr 12.7 EUR ile detayda belirtilen sabit gider sonrası katkı 12.4 EUR farklı kavramlar; yeni UI tek tutarlı net tahmin ve açık gider açıklaması kullanacak. Mevcut dev kartlar küçültülerek liste taranabilirliği artırılacak.
- Görsel kaynaklar: Documents/Codex/2026-09-27/handoff-md-dosyas-n-oku-mevcut/outputs/courier-rush-0.5.003-tr.png ve Documents/Codex/2026-09-24/mo/outputs/courier-rush-v050/courier-rush-v050-ui-jobs-selected.png.

## Kullanıcı netleştirmesi — 30 Eylül 2026

Yeni proje tamamen sıfırdan oluşturulacak. UI, eski oyunun ekranlarına benzemeyecek; ayrı bir görsel dil, bileşen sistemi ve ekran düzeni tasarlanacak. Eski projelerdeki ekranlar yalnız hangi bilginin/oyun davranışının faydalı olduğunu anlamak için kullanılır. Eski görsel tasarım, renk paleti, kart/navigasyon düzeni ve assetler yeni UI temeli olmayacak. Oyun mekanikleri ve kurallar referans alınabilir; mimari ve kod yeniden kurulacak. Bu netleştirme M0 raporu incelenirken ve M1/M8 acceptance kriterlerinde uygulanacak.
