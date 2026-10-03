# Console için kaynakla doğrulanmış yanıt notları

Kapsam: Parcelrise Tycoon 0.2.1 / code 4, mevcut değiştirilmemiş sürüm. Form etiketleri hesap/dile göre değişebilir; aşağıdakiler otomatik gönderim değil yayıncıya yardımcı yanıtlardır.

| Alan | Bu sürümde doğrulanmış durum |
| --- | --- |
| Uygulama erişimi | Tüm işlevler girişsiz erişilebilir. Hesap, davet, ücretli üyelik veya incelemeci kullanıcı adı/parolası yok. |
| Reklam | Reklam göstermez; reklam SDK'sı yok. |
| Veri güvenliği: uygulamanın topladığı/paylaştığı veriler | Uygulama cihaz dışına kullanıcı verisi iletmez. Toplama: hayır; paylaşma: hayır. Oyun etiketleri, ilerleme, ayarlar sadece cihazın özel depolamasında; bulut yedek/cihaz transferi kapalı. |
| Hesap oluşturma/silme | Kullanıcı hesabı oluşturulmaz. Yerel verilerin tümünü Android uygulama depolamasını temizleyerek veya uygulamayı kaldırarak silebilir. Oyun içi sıfırlama eski kayıtları arşivleyebilir; tam silme değildir. |
| Şifreleme / bağımsız güvenlik incelemesi | Ağ üzerinden aktarım yok. Formun toplama-hayır yolunu takip et; olmayan aktarım şifrelemesini veya yapılmamış bağımsız güvenlik incelemesini işaretleme. |
| Ödemeler/finans | EUR oyun içi sanal muhasebe birimidir. Gerçek para transferi, kredi, yatırım, kumar, ödül nakde çevirme, abonelik ve uygulama içi satın alma yok. Mağazada ücretli satma kararı ayrı yayıncı tercihidir. |
| İçerik | Lojistik/işletme simülasyonu. Şematik Alman şehir ve rota adları; paket/araç/şoför yönetimi. Kullanıcının girdiği şirket/şoför adları diğer oyunculara yayımlanmaz. Sohbet/UGC paylaşımı yok. |
| Cihaz erişimi | INTERNET, AD_ID, konum, kamera, mikrofon, kişiler veya paylaşılan depolama izni yok; son birleştirilmiş release manifest doğrulaması verification.json içinde. |
| Sağlık/haber/siyasi/kamu bağlantısı | Sağlık/haber uygulaması veya devlet kurumu uygulaması değil. Gerçek dünya kargo servisi sunmaz. |

Gizlilik URL'si yayımlanmış HTML politikasının adresi olmalı; `publisher-fields.json` alanları tamamlanmalı. Destek e-postasına kullanıcı kendisi yazarsa yayıncının o e-postayı işlemesi ayrıca politikada açıklanmalı.

İçerik derecelendirmesinde anketi gerçek oyun içeriğine göre yanıtla; otomatik rating sonucunu bu belge yerine kullan. Hedef yaş aralığı, çocuklara yönelik pazarlama kararı ve ülkeler yayıncı tarafından belirlenmeli. Çocuk hedef kitlesi seçimi ek Families şartlarını getirebilir. Bu belge yaş puanı veya üretim erişimi garantisi vermez.

Kaynak kanıtları: app/src/main/AndroidManifest.xml; res/xml/backup_rules.xml ve data_extraction_rules.xml; AndroidSaveRepository; gradle/libs.versions.toml; app/build.gradle.kts; release bağımlılık listesi; birleştirilmiş manifest. Eklenen SDK veya özelliklerde bu yanıtları aynen tekrar kullanma.

Resmî kaynaklar: https://support.google.com/googleplay/android-developer/answer/10787469?hl=en ve https://support.google.com/googleplay/android-developer/answer/9859455?hl=en
