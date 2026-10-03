# M17 — 0.2.1 Google Play yayın teslimi

3 Ekim 2026. Kullanıcı, mevcut geliştirici hesabı bulunan arkadaşının yayını kolayca yapabilmesi için gerekli dosyaları istedi. Destek e-postası kullanıcı tarafından `batuhanboran32@gmail.com` olarak verildi; yayıncı adı/hesap türü/ülkeler/fiyat/hedef kitle tahmin edilmedi. CC veya alt agent kullanılmadı.

## Değişiklik

- Uygulama 0.2.1 / versionCode 4. Ayarlar içinde çevrimdışı TR/EN gizlilik penceresi; yerel kayıt/arşivler, tam silme, destek iletişimi ve platform hizmetleri açıklanır.
- Motor, ekonomi, uygulama kimliği `com.canok.kargotycoon` ve kayıt şeması değişmedi.
- Release imzalama yalnız dört `PARCELRISE_UPLOAD_*` ortam değişkeniyle; eksik/karışık imza ayarları kontrollü hata verir. Varsayılan development veya debug anahtarı üretim için kullanılmaz.
- Yeni RSA3072 özel yükleme anahtarı, PKCS12 ve rastgele güçlü parolası kaynak deponun dışındaki özel teslim alanında oluşturuldu. Anahtar/parola GitHub'a gönderilmez; yalnız sertifika/fingerprint paylaşılabilir.
- Sertifika SHA256: `aa1d5fadbe0f784e3e165bbebe17c91ff453f846ae993b46b7f7424163417a96`.
- `tools/New-PlayUploadKey.ps1` ve `tools/Build-PlayRelease.ps1` gelecekteki yayın/güncellemeler için yardımcıdır. İlk yayında yaratılan anahtar sonraki güncellemelerde yeniden kullanılır.
- `store/google-play/publication/`: adım adım Console rehberi, beyan notları, yayıncı alanları ve HTTPS üzerinde barındırılabilecek çift dilli HTML gizlilik taslağı. 19 görselin 0.2.0 gerçek çekim kaynağı korunur; yeni sürüm bu ekranların davranışını değiştirmez. Sürüm notları 0.2.1 gizlilik eklemesini anlatır.

## Doğrulama

1. R8/resource-shrink içeren bundleRelease, assembleRelease ve lintRelease geçti. Lint 0 hata / önceki 1 OldTargetApi uyarısı. targetSdk36 mevcut Play minimumunu karşılar; kaynak: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-EN (3 Ekim 2026).
2. Google bundletool 1.18.3 validate ve AAB → universal APK dönüşümü geçti. AAB JAR imzası ve APK v2 imzası aynı özel yükleme sertifikasıyla doğrulandı. Self-signed yükleme sertifikasında JDK güven zinciri/zaman damgası uyarısı bir Play inceleme sonucu değildir.
3. APK zipalign -P16 kontrolü ve AAB içindeki arm64-v8a/x86_64 .so dosyalarının tüm ELF LOAD segmentlerinin en az 16384 bayt hizalaması geçti. Bundle config PAGE_ALIGNMENT_16K. Bu statik kontroller 16 KB fiziksel cihaz çalışma testi olarak sayılmaz.
4. Son AAB'den üretilen APK, mevcut emülatörün geliştirme sertifikasıyla yalnız kontrol amacıyla imzalanıp **install -r** ile kuruldu. Paket içeriği release/normal hızdır; mevcut ilerleme silinmedi. Teslim AAB ve APK özel yükleme anahtarıyla imzalıdır. Play'in yönettiği nihai uygulama imzası Console tarafından ayrıca uygulanır.
5. Görünür Pixel_10a/API37 üzerinde Ayarlar → gizlilik aç/kapat, TR → EN → TR ve destek e-posta metni doğrulandı; oyun normal Türkçe Panel ekranına bırakıldı. İlk kontrolde dialog içindeki stringResource cihaz diline dönüyordu; metinler pencereye girmeden seçili oyun dilinde çözülerek düzeltildi. Son iki dil kontrolü geçti.
6. Mağaza metin uzunlukları, 19 PNG boyutu/hash, manifest/paket sürümü/izinler/yedek ayarları ve herkese verilebilir pakette özel anahtar/parola bulunmadığı kontrol edildi. Gerçek parola dosyası yalnız ayrı özel imza paketindedir.

M16'nın 150 testi geçmiş 0.2.0 motor/doğrulama kaydıdır; bu küçük M17 güncellemesinde yeniden 150 test çalıştırılmış gibi raporlanmaz. Oyun motoru dosyaları değişmedi. Fiziksel telefon testi, gerçek 16 KB cihaz çalıştırması, Play Console yükleme/üretim incelemesi ve politika web barındırması yapılmadı.

## Teslim ve kalan yayıncı işlemleri

`ParcelriseTycoon-0.2.1-Publish-Handoff.zip`: AAB, APK, mapping, kaynak, mağaza dosyaları, gizlilik sayfası, rehber ve doğrulama/hash kayıtları. Ayrı `ParcelriseTycoon-0.2.1-Private-Signing.zip`: anahtar ve parola; sadece yayıncıya özel kanaldan verilir. Paket kaynak commit'i ve nihai artifact hash'leri `verification.json` içinde bulunur.

Yayıncı adını ve destek mesajı saklama/e-posta sağlayıcısı metnini tamamlamak, HTML'i herkese açık HTTPS adrese koymak, Console yaş/ülke/fiyat/rating beyanlarını doldurmak, dahili testten fiziksel telefon kontrolü ve hesabın istediği test/üretim erişimini tamamlamak yayıncıya kalır. Gmail adresine mesaj gönderilmedi. Herhangi bir kişiye GitHub veya Console yetkisi verilmedi. Gizlilik sayfası taslağı yayımlanmış/Google onaylı olarak sunulmaz.
