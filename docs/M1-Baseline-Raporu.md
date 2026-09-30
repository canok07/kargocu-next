# M1 — Yeni proje baseline

Tarih: 30 Eylül 2026. Yeni bağımsız proje: C:\Users\canok\AndroidStudioProjects\kargocu-next. Application ID com.canok.kargotycoon. Eski projeler değişmedi.

- Sıfırdan Kotlin/Compose proje, :app ve Android bağımsız :game modülü, version catalog, resmî Gradle ile üretilmiş wrapper ve resmî dağıtım SHA256 doğrulaması.
- Gerçek doğrulanan stack: AGP9.4.0, Gradle9.6.0, Kotlin/Compose/Serialization plugin2.4.20, Compose BOM2026.09.00; JBR25.0.3 runtime, JVM target17; minSdk24/compileSdk37/targetSdk36.
- Saf game testi: 5/5 geçti (clock rollback, hız dönüşümü, bounded offline süre ve taşma/invalid policy).
- Android lint: 0 hata, 5 uyarı. Uyarılar: daha yeni AGP/Gradle, target36 yerine37 önerisi; açık dataExtractionRules ve release resource shrinking önerileri. Pinler build ile doğrulandı; sırf latest uyarısı için değiştirilmedi. Backup/saves M7, release küçültme M11 kapsamı.
- assembleDebug geçti. Baseline APK yaklaşık11.8MB; henüz gameplay içermez.
- Compose v2 launch smoke: Pixel_10a(AVD), Android17/API37, emulator-5554: 1/1 geçti. İlk başarısız deneme eski transitif Espresso'nun InputManager.getInstance reflection çağrısıydı; resmî fix içeren Espresso3.7.0 sabit pinle düzeltildi.
- Manuel install + cold launch: adb success/status ok, uygulama PID doğrulandı; PID'ye sınırlı Logcat kontrolünde FATAL/ANR bulunmadı. Karşılama ekranı görseli kaydedildi.
- Engine-only -PgameOnly=true :game:test yapılandırması geçti; Android SDK gerektirmeyen görev yolu hazır.
- Testler sonrası henüz teslimat/save/fleet senaryoları çalıştırılmadı; ilgili mekanikler M2-M9'da implement edilecek.

Ürün UI'sı bağımsız olacak. Bu karşılama ekranı temel launch doğrulamasıdır; eski UI/asset aktarılmadı.

## Sonraki

M2-M7 domain/engine/fleet/drivers/parallel deliveries/persistence, ayrı mantıklı milestone commitleri ve deterministik testlerle izole CC görevinde hazırlanacak. Windows'ta diff/mimari/test incelemesi tamamlanmadan main'e alınmayacak.

Kaynak: [AndroidX Test resmî release notes](https://developer.android.com/jetpack/androidx/releases/test) Espresso3.7 satırlarında InputManager reflection düzeltmesini açıklar. Diğer sürüm kaynakları M0 belgelerinde.
