# Kargocu Next — M0 orkestratör kararları

30 Eylül 2026. M0-Discovery-Raporu.md araştırma/kanıt raporudur. Aşağıdaki kararlar M1 ve sonraki implementasyon için yetkili son tasarım planıdır; araştırmadaki önerilerle çelişirse bu belge uygulanır.

## M0 sonucu

GDD, güncel Windows kaynakları, curier şablonu, beş Linux Courier Rush referansı ve önceki tasarım/denetim belgeleri incelendi. 72 Windows kaynak/config dosyası yeniden hash kontrolünden geçirildi: 72 aynı, 0 değişen. Eski projeler salt okunur kaldı. CC görevi muoal0a0-79ad71 tek submit, sıfır status polling, izole worktree; model gpt-5.6-sol, Astra yok.

CC server otomatik kontrolü bir test kaydı döndürdü; bu yeni Android uygulamasının testi değildir. Yeni uygulama test/build/APK henüz yoktur. M0 tamamlandı; M1 ayrıca başlatılabilir. Sonraki aşama için kullanıcıdan tekrar onay gerekmiyor.

## Ürün ve kapsam

- Yeni proje yolu: C:\Users\canok\AndroidStudioProjects\kargocu-next. İsim: Kargo Tycoon. Bağımsız applicationId: com.canok.kargotycoon. Eski kargocu/curier paketleri ve kaynakları kullanılmayacak.
- Kullanıcının son netleştirmesi: UI eski oyuna benzemeyecek. Ayrı görsel dil, navigasyon, layout ve bileşenler; eski temalar, hero görseller, kart düzeni ve assetler taşınmayacak. Mekanikler/oyuncuya yararlı bilgiler referans olabilir.
- Başlangıç: 100 EUR, satılamaz kiralık panelvan, oyuncu kendi aracını sürer; işe alınmış şoför gerekmez. Başlangıç işleri rezerv/kapasite açısından yapılabilir olur.
- Zorunlu temel kapsam: günlük teklifler, oyun günü ve şirket kademesi, bağımsız işler/rota/sonuç, para ve açıklanabilir giderler, adet+kg kapasitesi, çoklu filo/alım/satım/kondisyon/bakım, şoför hire/fire/assign/unassign/maaş, araç başına tek aktif iş ve paralel teslimat, Almanya temalı şematik harita/bölge kapıları, TR/EN, tutorial, save/load/corruption recovery.
- Ayrı XP grind yerine gerçek ilerleme/iş/varlık/bölge şartlarından şirket kademesi türetilir. Eski 168 gerçek saat veya 24 gerçek saat teklif penceresi zorunlu tutulmaz.
- İlk sürüm kapsamı dışında: kredi/borsa, cloud/hesap/Firebase/Gemini, OSM navigasyon grafiği, karmaşık fatigue/shift/skill tree, Room/Hilt/Retrofit. Mimari bunları sonradan eklemeye izin verir.

## Zaman ve ekonomi tasarımının kapıları

- Gerçek bekleme süreleri eski Godot kodundan kopyalanmaz. Zaman hızlandırılmış oyun saati üzerinden yönetilir; ayrı Clock ve açık config kullanılır. Teklif penceresi ve progression kapıları oyun zamanına göre hesaplanır.
- Başlangıç hedefi 1 gerçek saniye = 1 oyun dakikası; gündelik iş 5–30 oyun dakikası aralığında. Kesin süreler M3 denge testleriyle belirlenir. Günü bitirme/ilerletme aynı engine komutunu kullanır; ayrı ekonomi yolu yaratmaz.
- Background service yoktur. Foreground tick ve resume ile bounded offline progression aynı deterministik reducer yolunu kullanır; çevrimdışında yeni iş otomatik alınmaz.
- Satın alma/hire gibi isteğe bağlı komutlar yeterli bakiye olmadan uygulanmaz. Beklenen ve gerçekleşen net/giderler UI'da açık ayrılır.
- cash >= 0 kuralı zaman ilerletmeyi kilitlemeyecek ve masrafları sessizce yok etmeyecek şekilde tasarlanır: işe başlarken zorunlu gider/riski rezervle; çalışılan günün maaş/bakımını tek kez kaydet; uygulama kapalıyken sınırsız idle günlük gider üretme. Tam muhasebe/reserve şeması M3 ekonomi test kapısıdır. Yetersiz rezervde işe başlamama nedeni UI'da görünür.
- Ekonomi başlangıç aracı→kendi aracı→şoför→ikinci araç döngüsünde erişilebilir olmalı. Gerçek para fiyatlarına körü körüne bağlanmadan seeded 14/30 oyun günü simülasyonu ve soft-lock testleriyle dengelenir.

## Mimari

İki gerekçeli modül: :game saf Kotlin/JVM engine, modeller, katalog ve deterministic testler; :app Android persistence/DI/ViewModels/Compose. Daha fazla modül eklenmez. :game Android/Compose'a bağımlı olamaz. Bunun amacı motoru SDK/emülatör gerektirmeden test edebilmektir.

Immutable tek GameState, komut/reducer/event modeli, enjekte clock/RNG ve config, ekran bazlı ViewModel projection'ları. Application-scoped store komutları tek writer ile sıralar. Paralel teslimat state modelidir; ayrı dosya/state writer oluşturmaz. Revision sırası ve completion idempotency zorunludur.

Save envelope version/catalog/revision/payload checksum içerir; atomic replace, last-known-good, sıkı validation, unknown future version korunması, bounded ledger/history. Bozuk dosya üzerine otomatik boş yeni oyun yazılmaz. Eski importer opsiyoneldir; otomatik eski sandbox erişimi varsayılmaz.

## M1 sabit teknoloji seti

- AGP 9.4.0, Gradle 9.6.0 (+SHA256), Kotlin/Compose/Serialization plugin 2.4.20.
- Compose BOM 2026.09.00, Lifecycle 2.11.0, Navigation Compose 2.10.2, Activity Compose 1.13.0, Core 1.19.1.
- Coroutines/test 1.11.0, Serialization JSON 1.11.0. Gerekmeyen dependency M1'de erken eklenmez; katalogda pin ve sonraki milestone'da explicit kullanım.
- minSdk 24; compileSdk 37; targetSdk 36 başlangıç. SDK 37 testinden sonra hedef 37 yükseltmesi ayrı değerlendirilir.
- Windows build runtime Android Studio JBR 25.0.3; kaynak/bytecode hedefi 17. JDK17 kurulmuş gibi raporlanmaz. Gradle9.6 Java25'i destekler. Linux runtime ayrıca gerçek komutla doğrulanacak.
- Built-in Android Kotlin ile Kotlin JVM/Compose/plugin sürümlerinin eşleşmesi dependency resolution ve gerçek compilation ile doğrulanır. Sorun olursa root cause bulunup minimum düzeltme yapılır; rastgele sürüm değişikliği yok.
- Version catalog merkezi; dynamic dependency yok. Gereksiz Firebase/Gemini/demo/Google Services yok. Gerekli küçük vector ikonlar yeni oluşturulur.

Kaynaklar M0-Windows-Ortam.md ve M0-Discovery-Raporu.md içinde. Bu set resmî yayınlara göre planlanmıştır; başarılı build iddiası değildir.

## Milestone sırası

M0 Discovery (tamamlandı) → M1 yeni proje/baseline/Git/APK → M2 temel domain/katalog → M3 engine/gün/jobs/economy/risk/progression → M4 fleet → M5 drivers → M6 parallel deliveries → M7 versioned save/recovery → M8 tamamen yeni Compose UI → M9 tutorial/UX/TR-EN → M10 Android validation → M11 balance/performance/polish/regression.

Her milestone mantıklı commit ve değişikliğe uygun test/compile/lint/debug build kapısından geçer. Final emulator senaryosu M0-Kabul-Kriterleri.md içindedir. Fiziksel cihaz varsa aynı senaryo çalıştırılır; fiziksel cihaz yoksa tamamlandı denmez ve sınırlama açık raporlanır. Fiziksel cihaz yokluğu emülatörde yapılabilecek işleri durdurmaz.

## Dur komutu

Kullanıcı dur dediğinde aktif CC görevi korelasyon kimliğinden bulunarak iptal edilir; sunucu kapatılmaz. Aktif yerel build/monitor durdurulur; yeni işlem başlatılmaz. Kaldığı commit/milestone, değişiklikler ve devam koşulu kaydedilir. Yeniden başlatma kullanıcı talimatıyla olur.
