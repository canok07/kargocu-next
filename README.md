# Kargo Tycoon — 0.1.0

Sıfırdan geliştirilen Android lojistik oyunu. Eski Kargocu/Courier Rush projeleri yalnız referanstır; bu oyunun kodu, ekranları ve çizimleri yeni oluşturulmuştur.

Oyuncu 100 EUR ve kendi sürdüğü kiralık panelvanla başlar. İş kabulü, maliyet önizlemesi, teslimat ve ödeme, araç alım/satımı/bakımı, şoför işe alma/atama, paralel işler, şirket ve bölge ilerlemesi, Almanya temalı şematik harita, öğretici, TR/EN ve yerel kayıt/kurtarma akışı uygulanmıştır. Türkçedeki Panel, İngilizcedeki Dashboard ekranıdır.

Zaman: 1 gerçek saniye = 1 oyun dakikası. Yeniden açılışta geçen süre en fazla bir oyun günü uygulanır; çevrimdışında yeni iş otomatik kabul edilmez.

## Açma ve çalıştırma

Android Studio'da bu proje klasörünü aç, Gradle JDK olarak desteklenen Studio JBR'yi seç, senkronizasyonu tamamla ve app yapılandırmasını çalıştır. Windows doğrulaması Studio JBR 25 ile yapıldı; varsayılan Java 8 kullanılmamalıdır.

- Android 7.0/API24 ve üzeri; compileSdk37, targetSdk36.
- Bağımsız uygulama kimliği: com.canok.kargotycoon.
- Sabit bağımlılıklar: gradle/libs.versions.toml; JVM hedefi17.
- app Android/Compose ekranları ve dosya kaydı; game saf Kotlin/JVM oyun motorudur. Hesaplamalar ekranlarda yapılmaz.

## Doğrulama — 2 Ekim 2026

78 motor testi ve Pixel_10a Android17/API37 emülatöründe 21 Android testi geçti. Gerçek süreç kapatma/yeniden açma, iki paralel teslimatın yalnız bir kez ödenmesi, kayıt hata/kurtarma, TR/EN, büyük yazı, koyu görünüm ve klavye akışları kontrol edildi. Küçültülmüş release APK, test anahtarıyla emülatörde açıldı; ilk teslimat sonrası bakiye ve öğretici tekrar açılışta korundu.

Kontroller: game:test, app:connectedDebugAndroidTest, app:assembleDebug, app:assembleRelease, app:lintDebug. Lint: 0 hata; bilinçli targetSdk36 tercihi için 1 OldTargetApi uyarısı. Son toplam ağırlık metni düzeltmesinden sonra debug/release ve lint yeniden geçti.

Fiziksel cihaz testi ve mağaza yayını yapılmadı. Release derlemesi üretim imzası içermez; dağıtım anahtarı ayrıca gereklidir. Ağ/hesap/cloud bağımlılığı yoktur.

M0 karar ve kabul belgeleri docs altında bulunur; bunlar tarihsel planı korur. Doğrulanan oyun kaynağı m11-verified-20261002 etiketiyle kayıtlıdır. Kullanıcı DUR dediğinde yeni çalışma başlatılmaz, aktif işler durdurulur ve devam için açık talimat beklenir.
