# Parcelrise Tycoon — arkadaşına yayın teslimi

Bu paket **ilk Google Play yayını** için hazırlanır. Hesapta aynı paket kimliğine sahip kayıtlı uygulama ve yükleme anahtarı zaten varsa yeni anahtarı kullanmadan önce Console'daki sertifikayla karşılaştır. Yeni uygulamada Google tarafından oluşturulan uygulama imza anahtarıyla Play App Signing kullanılabilir. Buradaki özel anahtar yükleme anahtarıdır.

## Teslim dosyaları

- `release/ParcelriseTycoon-0.2.1.aab`: Console'a yüklenecek, özel yükleme anahtarıyla imzalı paket; versionCode 4.
- `release/ParcelriseTycoon-0.2.1.apk`: doğrudan cihaz kontrolü için aynı anahtarla imzalı APK.
- `store/`: EN/TR mağaza metinleri, 512×512 ikon, 1024×500 tanıtım görselleri ve her dilde sekiz gerçek ekran görüntüsü.
- `privacy/index.html`: yayıncı ve e-posta alanları tamamlanıp herkese açık bir HTTPS adresinde yayımlanacak gizlilik politikası.
- `source/`: geliştirilebilir proje; `tools/Build-PlayRelease.ps1` sonraki güncellemeleri aynı yükleme anahtarıyla üretir.
- `SHA256SUMS.txt` ve `verification.json`: dosya bütünlüğü ve uygulama doğrulama bilgileri.
- Ayrı **Private-Signing.zip**: yükleme anahtarı, parola dosyası ve sertifika. Bu dosyayı GitHub'a veya herkese açık bir yere yükleme; sadece yayıncıya güvenli şekilde aktar. Anahtar ve parola iki güvenli yedekte tutulmalı.

Geliştirme APK'sı farklı bir anahtarla imzalıydı. Üzerine bu APK kurulamayabilir; ilk denemeyi başka cihazda yap veya eski sürümü kaldırmadan önce mevcut ilerlemeyi kaybedeceğini hesaba kat. Play'in Google tarafından yönetilen imza anahtarı da yükleme anahtarından farklı olabilir. Mağaza kurulumu testini Console'un dahili test kanalından yap.

## İlk yayında adım adım

1. `publisher-fields.json` içindeki yayıncı adı ve destek e-postasını doldur. `privacy/index.html` içindeki aynı alanları değiştir; dosyayı giriş gerektirmeyen, sabit HTTPS adresine koy. Bir metin dosyası/PDF bağlantısı yerine bu HTML sayfasını kullan. Sayfayı gizli sekmede açıp yer tutucu kalmadığını doğrula. Yayıncı destek e-postasına gelen mesajları nasıl/süreyle sakladığını kendi gerçek uygulamana göre politika taslağında düzenle.
2. Console'da **Parcelrise Tycoon**, tür **Oyun**; birincil dil English (United States). Ücretli/ücretsiz seçimini yayıncı yapar; uygulamada gerçek para satın alma sistemi yoktur. Yeni kayıt aç, Play App Signing'i etkinleştir.
3. `store/en-US/` ve `store/tr-TR/` metinlerini ilgili dil alanlarına yapıştır. `store/art/` ikonunu ve ilgili tanıtım görselini yükle. Her dilin `screenshots/` klasöründeki sekiz PNG'yi sırayla yükle.
4. Destek e-postasını ve yayımladığın gizlilik politikası URL'sini mağaza alanlarına ekle. `PLAY-CONSOLE-BEYANLARI.md` ile uygulama içeriği bölümlerini doldur. Hedef kitle/ülkeler/fiyat/hesap sahipliği seçimleri yayıncıya aittir; taslak kesin yaş derecelendirmesi iddia etmez.
5. Dahili test sürümü aç ve `release/ParcelriseTycoon-0.2.1.aab` yükle. Uygulama kimliği **com.canok.kargotycoon**, sürüm **0.2.1 / 4** olmalı. `whats-new.txt` ilgili dilde sürüm notudur. Play'in AAB işleme ve cihaz uyumluluk sonuçlarını incele; uyarıları gerçek çıktıya göre değerlendir.
6. Test bağlantısından fiziksel telefona kur. Yeni oyun → ilk iş → teslimat/ödeme → kapat/aç → kayıt devamı; EN/TR → Diğer/Ayarlar/Gizlilik politikası; mümkünse Android 7+ ve güncel Android cihazlarında dene. Cihaz testi ve Console ön lansman raporu tamamlandı olarak önceden işaretlenmedi.
7. Console hesabın için kapalı test veya üretim erişimi başvurusu istiyorsa tamamla. **13 Kasım 2023'ten sonra açılmış kişisel hesaplarda** en az 12 testçinin aralıksız 14 gün katılımı ve ardından üretim erişimi başvurusu gerekir. Eski hesap/kuruluş hesabı durumunu Console'dan kontrol et; geliştirici hesabının olması tek başına üretim erişimini kanıtlamaz.
8. Son beyanları, bölge/fiyat/iletişim ve test sonuçlarını gözden geçir; üretim sürümünü incelemeye gönder. Bu paket yayımlanmış veya Google tarafından onaylanmış değildir.

## İleride geliştirme

Android Studio'da `source/` aç. Proje README/AGENTS ve docs kararlarını oku. :game motor, :app Android/Compose katmanıdır. Mevcut kayıt biçimi ve paket kimliği korunmalı; sonraki yükleme için versionCode 4'ten büyük olmalı. Parolayı veya anahtarı kaynak içine kopyalama.

Windows örneği: `tools/Build-PlayRelease.ps1 -CredentialsFile '<özel-klasör>/upload-credentials.json' -JavaHome '<Android Studio>/jbr' -Verify`. `local.properties` dosyasındaki SDK yolunu kendi bilgisayarına göre oluştur; teslim kaynak paketinde makineye özgü dosya yoktur. İmzalama değişkenleri verilmezse release imzasız üretilir; onu Console'a yükleme.

Reklam/analitik/bulut/ödeme veya yeni izin eklenirse gizlilik politikası, veri güvenliği ve mağaza beyanları tekrar incelenmeli. Geçmiş motor testleri bu tür gelecekteki değişiklikleri doğrulamaz.

## Resmî kaynaklar — 3 Ekim 2026 kontrolü

- İmzalama: https://developer.android.com/studio/publish/app-signing
- Veri güvenliği: https://support.google.com/googleplay/android-developer/answer/10787469?hl=en
- Gizlilik politikası: https://support.google.com/googleplay/android-developer/answer/10144311?hl=en
- Hesap test şartları: https://support.google.com/googleplay/android-developer/answer/14151465?hl=en
- Mağaza alanları: https://support.google.com/googleplay/android-developer/answer/9859152?hl=en

Kalan yayıncı alanları `publisher-fields.json` içinde açıkça listelenmiştir; hesap bilgileri tahmin edilmemiştir.
