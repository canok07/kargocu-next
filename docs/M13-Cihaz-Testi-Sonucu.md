# M13 — Cihaz testi sonucu ve durdurma noktası

2 Ekim 2026. Taban: `d7d2dc40154932f4aba687d6c943e18d158b6e6b`.
Ürün kaynakları M12 ile aynı; değişiklikler Android testleri ve bu raporlardır.
CC görevi `murf695b-00afda` yetersiz kredi nedeniyle kod/test çalıştırmadan
sonlandı. Kullanıcının önceki CC kotası biterse Codex'in devam etmesi talimatı
uygulandı. Eski referans projeler değiştirilmedi.

## Koşu ve bulgular

- Görünür Pixel_10a emülatörü, Android17/API37, `-no-snapshot -gpu software`.
  NVIDIA donanım grafik yolu kullanılmadı; bu fiziksel cihaz sonucu değildir.
- Gerçek soğuk açılıştan başlatma düğmesine basınca welcome → dashboard
  geçişi ve 100 EUR başlangıç bakiyesi görüldü. Aynı uygulamanın welcome ve
  dashboard ekranları mevcut; ayrı ikinci bir uygulama/UI eklenmedi.
- İlk Android koşusu: 30 test, 28 başarılı, 2 başarısız. İki stres testi
  `game.json.new` konumundaki boş klasörün yazmayı engelleyeceğini varsayıyordu.
  AtomicFile okuma sırasında boş geçici klasörü temizlediğinden yazma başarılı
  oluyordu. Mevcut SessionPersistenceTest zaten dolu klasör kullanıyordu.
- Her iki stres testinde geçici klasöre engel dosyası eklendi. Hata bekleme
  assertion'ları kaldırılmadı; repository testinde başarısız yazma sonrası
  önceki kaydın byte düzeyinde korunması ayrıca kontrol edildi.
- AppUiTest'e gerçek engine komutlarıyla para kazanılan ek kariyer testi
  eklendi: araç alımı, şoför işe alma, işten çıkarmayı iptal/onay, araç satışını
  iptal/onay, yalnız gerçek işlemin para/ledger etkisi ve kaydı yeniden açma.

## Sonuç

Son `:app:connectedDebugAndroidTest :app:lintDebug --offline --console=plain`
koşusu 1 dakika 13 saniyede başarılı. Android rapor zamanı:
`2026-10-02T20:41:17` (emülatörün yerel saati).

| Android test sınıfı | Test | Başarısız |
| --- | ---: | ---: |
| AndroidSaveRepositoryTest | 9 | 0 |
| AppUiTest | 8 | 0 |
| LaunchSmokeTest | 1 | 0 |
| OrchestratorDeviceCareerTest | 1 | 0 |
| SessionPersistenceTest | 3 | 0 |
| StressAndroidSaveRepositoryTest | 5 | 0 |
| StressSessionControllerTest | 4 | 0 |
| Toplam | 31 | 0 |

Hata ve atlanan test de 0. M12'nin yeni 9 stres testi artık gerçekten cihazda
çalıştı. Bu koşu gerçek process kill testi değildir; session yeniden açma ve
Activity recreation, process ölümünden ayrı kabul edilir.

Restore doğrulamasında `:game:stressTest` 20 testle yeniden geçti. Değişmeyen
motorun 81 normal test sonucu Gradle önbelleğinden geldi; 81'i bu oturumda
yeniden yürütülmüş saymıyoruz. Debug ve Android test APK derlemeleri başarılı.
Lint 0 hata; sabit targetSdk36 için mevcut 1 OldTargetApi uyarısı.

## Yerel teslim ve kalanlar

Yeni çalışma klasörü:
`C:\Users\canok\Documents\Codex\2026-09-30\in\work\kargocu-next`.

Yerel outputs içinde:

- `KargoTycoon-20261002-M13-debug.apk`, SHA256:
  `42D2A6A7991A4EBFE203009764163E9A6A65BBF7462DAAD41082E97BD14D368F`.
- `M13-Android-31-Passed.xml`, `M13-Lint.xml`.
- İlk başarısız koşunun `M13-Android-Baseline-30-2fail.xml` kanıtı.
- Başlangıç/dashboard ekran kanıtı ve emülatör günlükleri.

Kullanıcı test biter bitmez durulmasını istedi. Yeni test/geliştirme/yükleme
başlatılmadı. Gerçek 60 dakikalık görünür test, bu M12 ürün kaynağında tam
process-kill/rotation işlev matrisi, fiziksel cihaz, üretim imzası ve mağaza
yayını tamamlandı diye raporlanmıyor. GitHub'daki önceki yedek değişmedi;
M13 test/doküman değişiklikleri yerel kaydedildi. Açık devam talimatı beklenir.
