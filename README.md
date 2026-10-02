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

## M11 doğrulaması — 2 Ekim 2026

78 motor testi ve Pixel_10a Android17/API37 emülatöründe 21 Android testi geçti. Gerçek süreç kapatma/yeniden açma, iki paralel teslimatın yalnız bir kez ödenmesi, kayıt hata/kurtarma, TR/EN, büyük yazı, koyu görünüm ve klavye akışları kontrol edildi. Küçültülmüş release APK, test anahtarıyla emülatörde açıldı; ilk teslimat sonrası bakiye ve öğretici tekrar açılışta korundu.

Kontroller: game:test, app:connectedDebugAndroidTest, app:assembleDebug, app:assembleRelease, app:lintDebug. Lint: 0 hata; bilinçli targetSdk36 tercihi için 1 OldTargetApi uyarısı. Son toplam ağırlık metni düzeltmesinden sonra debug/release ve lint yeniden geçti.

Fiziksel cihaz testi ve mağaza yayını yapılmadı. Release derlemesi üretim imzası içermez; dağıtım anahtarı ayrıca gereklidir. Ağ/hesap/cloud bağımlılığı yoktur.

## M12 stres düzeltmeleri — 2 Ekim 2026

Son oyun kodu: db5030a9fde008b378963b2f201f5487844870da. Kiralık aracın sıfır maliyetli onarım/bakım işlemleri artık sıfır tutarlı muhasebe satırı üretmeden uygulanır. Aşırı iç içe bozuk JSON'un ayrıştırma/deserialization sırasında oluşturduğu StackOverflowError kontrollü bozuk kayıt sonucuna çevrilir.

Windows'ta bu kod üzerinde 81 normal motor testi ve ayrı 20 ağır stres testi geçti; debug, Android test paketi, küçültülmüş release ve lint derlendi. Ağır koşu: 120 seed ile 240.120 komut denemesi ve 120 deterministik tekrar; ayrıca 8 seed ile 365'er oyun günü ve 6.144 bozuk/değiştirilmiş kayıt varyantı. Komutların 152.698'i uygulandı, 87.422'si reddedildi. Zararsız checksum harf büyüklüğü değişimi taşıyan 5 dosya aynı state ile okundu; 4.091 diğer byte varyantı ve 2.048 checksum-geçerli/geçersiz-state varyantı reddedildi.

Kiralık araç bakım hatası görünür ayrı cihazda önce yeniden üretildi; düzeltme sonrası bakım ve tekrar bakım engeli geçti. Şoför ataması kaldırma ve işten çıkarma (950 cent tazminat) kayda doğru geçti. Son UI akışı cihaz bağlantısının kaybolmasıyla kesildi; sebep belirlenmedi. Kullanıcı testleri durdurdu. Yeni 9 Android stres testi yalnız derlendi; cihazda çalıştırılmadı. Kalan işlev matrisi ve gerçek 60 dakikalık görünür test tamamlanmadı; uzun süre testi başlamadı. Önceki M11 Android sonuçları bu yeni sürümün tam cihaz doğrulaması olarak sayılmaz.

Stres testini ayrıca çalıştırmak için: `gradlew :game:stressTest`. Normal `:game:test` ağır stres paketini içermez. Ayrıntılar docs/M12-Stres-Kontrolu-20261002.md içindedir.

M0 karar ve kabul belgeleri docs altında tarihsel planı korur. M11 oyun kaynağı m11-verified-20261002 etiketiyle, M12 düzeltmeleri m12-stress-fixes-20261002 etiketiyle kayıtlıdır. Kullanıcı DUR dediğinde yeni çalışma başlatılmaz, aktif işler durdurulur ve testlere devam için açık talimat beklenir.
