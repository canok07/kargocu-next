# Kargocu Next — M0 salt-okunur keşif raporu

**Tarih:** 30 Eylül 2026, Europe/Berlin  
**Kapsam:** Yalnız M0 analiz ve tasarım kararı. Yeni Android projesi, uygulama kodu, APK/AAB, bağımlılık kurulumu veya eski proje değişikliği yoktur.

## 1. Yönetici özeti

Yeni uygulama eski `kargocu`, `curier` veya Godot Courier Rush kodunun devamı olmayacaktır. Yetkili gereksinim bunu açıkça ister: eski kaynaklar yalnız bilgi/ilhamdır; yeni proje Kotlin + Jetpack Compose ile sıfırdan kurulacaktır (`USER_REQUIREMENTS.txt:3-8, 39-58, 64-96, 234-252, 1011-1044`).

Kanıt üç ayrı ürün çizgisi gösteriyor:

1. **Windows `kargocu` Compose prototipi:** freelance başlangıç, gün sonu toplu simülasyon, rota kiralama, tek liste sırasına göre araç/şoför eşleştirme, kredi, moral, kondisyon, TR/EN ve `SharedPreferences`/Gson kaydı var. Ancak gerçek iş nesnesi, aktif teslimat/deadline, kg kapasitesi, satış, açık araç-şoför ataması, paralel teslimat ve versiyonlu/atomik kayıt yok.
2. **Windows `curier`:** launcher activity’si olmayan boş AppCompat şablonu; oyun kanıtı değildir.
3. **Linux Courier Rush Godot/Node ailesi:** başlangıç kiralık araç, veri odaklı fiziksel ekonomi, filo kayıt ID’leri, paket + kg kapasitesi, çoklu şoför/atama/çıkarma, seeded risk, araç başına aktif iş, paralel teslimat, bölgeler, 121 konum, kayıt v6 ve v2–v5 göçü gerçekten kodlanmıştır. Bunlar **Compose kodu değildir** ve doğrudan taşınmayacaktır.

Önerilen telefon öncelikli MVP, iki eski yönü tek tutarlı modelde birleştirir:

- Başlangıç **100 EUR + satılamaz kiralık panelvan**; freelance `500 EUR / 75 EUR / 20 XP` döngüsü yok.
- Teklifler oyun gününe göre yenilenir; kabul edilen teslimatlar canlı `dueAt` ile ilerler ve uygulama açıldığında/öndeyken deterministik olarak tamamlanır. Zorunlu “Günü Bitir” toplu simülasyonu ve arka plan servisi yok.
- Bir araç aynı anda en fazla bir aktif teslimat taşır; farklı araçlar paralel çalışır.
- Şirket kademesi, görev/para/varlık/bölge kapılarından **türetilen** bir gösterge olur; ayrı XP grind’ı yok.
- Ağ, hesap, Firebase/Gemini, Retrofit, ücretli servis, sürekli background service, Hilt ve Room MVP’ye girmez.
- Saf Kotlin deterministik motor + immutable tek state + komut/olay akışı + tek-yazar store + atomik, sürümlü JSON kayıt kullanılır.

**M1’e geçiş kararı:** Bu rapor orchestrator tarafından onaylanmadan `C:\Users\canok\AndroidStudioProjects\kargocu-next` oluşturulmamalıdır.

## 2. Kapsam, yöntem ve sınırlamalar

### 2.1 İncelenen arşiv

- Kaynak: `/tmp/kargocu-next-m0-reference-20260930.zip`
- Arşiv SHA-256: `16beb93e50c56725c960b6e6ec389cae20e93a2285fc6ab7051729dbb74cd4af`
- ZIP girdisi: 105; güvenli çıkarım öncesi mutlak yol, `..` kaçışı ve symlink kontrolü yapıldı, risk bulunmadı.
- Çıkarılan dosya: 82.
- `source-manifest.json`: 72 kayıt (`kargocu` 45, `curier` 27); **72/72 dosya mevcut ve SHA-256 eşleşiyor**, eksik/uyuşmazlık 0.
- Manifest dışındaki 10 dosya: GDD, yetkili gereksinimler, tasarım/yol haritası/audit ve iki “Git değil” durum kaydı.

### 2.2 Kanıt hiyerarşisi

1. `USER_REQUIREMENTS.txt` ürün ve süreç için yetkilidir.
2. 30 Eylül 2026 arşivindeki Windows kaynak snapshot’ı, mevcut Windows kodu için yetkili anlık görüntüdür.
3. Kaynak kodu, aynı projedeki README/GDD iddiasından daha güçlü uygulama kanıtıdır.
4. Linux Git HEAD ve çalışma ağacı ayrı ayrı kaydedilmiştir; kirli çalışma ağacındaki içerik HEAD ile eşit varsayılmamıştır.
5. Godot/Node özelliği hiçbir yerde Compose özelliği olarak sınıflandırılmamıştır.

### 2.3 Sınırlamalar ve erişilemeyen kanıt

- Gerçek Windows yolları bu Linux oturumunda mount edilmedi; yalnız hash doğrulanmış arşiv snapshot’ı okundu.
- Windows `kargocu` ve `curier` Git deposu değil (`*-git-status.txt:1-2`); branch/commit geçmişi yok.
- Gerçek cihazdaki uygulama özel depolaması veya gerçek save dosyaları incelenmedi.
- Windows Android Studio/JDK/SDK kurulum durumu, Gradle Sync, emulator ve fiziksel cihaz durumu çalıştırılarak doğrulanmadı. `local.properties` okunmadı; credential/kişisel IDE workspace içeriği incelenmedi.
- Eski Godot projeleri build/test edilmedi; rapordaki eski test sonuçları yalnız mevcut belge/tarihsel kanıttır.
- Genel filesystem taraması yapılmadı; yalnız verilen arşiv, mevcut izole worktree ve adı verilen beş `/home/can/projects/...` referansı incelendi.
- Resmi ağ sayfalarına 30 Eylül 2026’da erişilebildi; önerilen sürümler **yayında olduğu doğrulanan** sürümlerdir, bu makinede kurulu veya yeni projede build edilmiş sayılmaz.

## 3. Referans envanteri

| Referans | Git/başlangıç durumu | Motor/dil/platform | Uygulanmış kanıt | Yalnız öneri/eksik |
|---|---|---|---|---|
| Arşiv `kargocu` | Git yok | Kotlin, Jetpack Compose, Android | Gün sonu simülasyon, freelance, rota/araç/şoför listeleri, kondisyon/moral/kredi, TR/EN, Canvas radar, Gson save | Aktif iş modeli, kg kapasitesi, açık atama, satış, paralel iş, versioned/atomic save yok |
| Arşiv `curier` | Git yok | Kotlin/Java Android AppCompat şablonu | Build config ve test şablonları | Launcher activity ve oyun kodu yok |
| Mevcut izole worktree | `cc/task-muoal0a0-79ad71`, `95710930...`, başlangıç temiz | Godot 4/GDScript + Node JS, Android hedefli | Hohenfeld, filo/risk/paralel iş/data/i18n/save v6 kodu | Compose değildir; rapor öncesi yeni Android kodu yok |
| `/home/can/projects/courier-rush` | `cc/task-mufvgszt-d80b80`, `95710930...`; önceden kirli | Aynı Godot/Node ailesi | HEAD geçmişinde filo, paralel iş, navigation, i18n; çalışma ağacında ayrıca kullanıcı değişiklikleri | Kirli olduğu için uncommitted içerik “release” sayılmaz |
| `courier-rush-05001` | `courier/0.5.001`, `1fb6945b...`; temiz | Godot/Node + Android export | Freiburg snapshot, 121 konum, ekonomi ve Android araçları | Yeni Kotlin uygulama değil |
| `courier-rush-step2-20260926` | `courier/0.5.001`, `1fb6945b...`; önceden yoğun kirli | Godot/Node, Freiburg veri dönüşümü | Yön/harita hazırlıkları çalışma ağacında | HEAD ile çalışma ağacı ayrılmalı |
| `courier-rush-step4-20260926` | `courier/0.5.001`, `f9595e2...`; önceden kirli | Godot/Node, Freiburg/OSM | Yönlü rota commit’i; şematik harita değişiklikleri çalışma ağacında | Fiziksel cihaz doğrulaması yok |
| `courier-rush-step5-20260926` | `.git` yok | Godot/Node snapshot, `0.5.002` | 5 adımlı tutorial/kademeli UI snapshot’ı | Commit/status kanıtı yok |

### 3.1 Windows `kargocu` teknik gözlemleri

- Modeller: rastgele UUID araç/şoför/rota ID’leri, araç `price`, `dailyCost`, yalnız adet `capacity`, kondisyon, yaş, kilometre ve arıza hesabı (`Models.kt:5-32`); şoför deneyim/maaş/verim/bakım/moral (`35-43`); oyuncu para/gün/seviye/XP, mutable filo/şoför/rota, kredi, HQ personeli, zorluk, depo, hız ve dil (`79-100`).
- Döngü: custom `CoroutineScope(Default)` sonsuz ticker ve 2/5/10 saniyede `startNextDay()` (`GameViewModel.kt:19-79`); zaman/RNG enjekte edilmemiştir.
- Ekonomi/içerik ViewModel içine gömülüdür (`81-126`). Gün sonu route index’i aynı index araç ve şoförle eşleştirir (`150-155`); görev/araç atama kimliği yoktur.
- Paket türü rastgele üretilir, gelir ve Express cezası hesaplanır (`164-224`); olaylar da seed’sizdir (`232-250`). Testler tekrar üretilebilir değildir.
- Satın alma ve listeler `copy()` içinde mevcut `MutableList.apply { add(...) }` ile yerinde değiştirilir (`325-357`); immutable state sözü bozulur ve concurrent ticker/UI komutları için tek-yazar garantisi yoktur.
- Save: `kargocu_save_prefs` içinde bağımsız `player_state` ve `daily_reports` JSON string’leri, `apply()` ile iki key (`SaveManager.kt:9-20`). `saveVersion`, checksum, transaction, atomik envelope, last-known-good ve invariant doğrulaması yoktur. Parse hatası sessiz `null`/boş listeye döner (`22-38`).
- UI: tek `GameScreen.kt` dashboard/market/fleet/settings akışını toplar; `collectAsState()` lifecycle-aware değildir (`GameScreen.kt:47-85`). Bazı XP göstergesi motorun `level*250` eşiği yerine `level*100` gösterir (`211-213` vs. `GameViewModel.kt:291-300`).
- Testler yalnız başlangıç/freelance/rota/kiralık alımı ve kaba ekonomi aralığını kapsar (`GameViewModelTest.kt`, `EconomySimulationTest.kt`). Save, risk seed’i, kapasite, satış, atama, paralel iş ve migration testi yoktur; bu M0’da çalıştırılmadı.
- Demo kalıntısı: Firebase AI `17.+`, Google Services plugin, `BakingScreen/BakingViewModel`, `gemini-flash-latest` ve baking string’leri (`libs.versions.toml:12,31,36`; `app/build.gradle.kts:1-5,52`; `BakingViewModel.kt:6-23`). Yeni projeye taşınmamalıdır.
- Gözlenen config: AGP `9.4.1`, Gradle `9.6.0`, toolchain `25`, Kotlin `2.2.10`, Compose BOM `2026.02.01`, `compile/target/minSdk=37`; Lifecycle `2.6.1` catalog + ayrıca `2.7.0`, Activity `1.8.0`. Bu set tutarsız/eski karışıktır ve `minSdk 37` gereksiz derecede yüksektir.

### 3.2 Linux Godot/Node kanıtı

Mevcut izole worktree kaynakları:

- Başlangıç kiralık araç `v0`, filo/şoför/unlocked region/ledger/aktif işler ve kayıt v6 (`scripts/game_state.gd:12-18, 45-75, 97-111`).
- Paket + ağırlık + soğuk zincir kapasitesi (`191-207`; `data/vehicles.json:6-103`; `data/jobs.json:6-107`).
- Paralel model: araç başına bir iş; farklı araçlar ayrı aktif işler (`accept_offer`, `496-552`; Node test `economy.test.mjs:527-563`).
- Araç alım/satım (`554-609`), çoklu şoför/hire/fire/assign/unassign (`653-764`), bölge kapıları (`766-816`).
- Seed’li gecikme/hasar/şoför hatası (`474-483, 511-546`); aynı girdiye aynı sonuç.
- Save v6 alanları ve v2–v5 göçü (`899-924, 976-1065`), fakat doğrudan asıl dosyaya yazım (`926-932`) ve yetersiz invariant doğrulaması vardır.
- 121 mahalle/konum, 7 araç, 5 paket türü, 3 risk seviyesi, 4 bölge; bu kod Compose değildir.
- UI tek `scripts/main.gd` dosyasında yaklaşık 2.000+ satıra büyümüştür; yeni mimaride aynı yoğunlaşma tekrarlanmamalıdır.

Freiburg snapshot ailesi gerçek OSM/yönlü rota fikrini ve 121 teslimat konumunu taşır (`step5 DESIGN.md:28-34`), fakat lisans/attribution, veri boyutu ve gerçek navigasyon algısı getirir. MVP’de şematik oyun haritası korunacak; tam OSM rota grafiği deferred olacaktır.

## 4. Gereksinim–kanıt karşılaştırması

**Kısaltmalar:** C = Windows Compose `kargocu`; G = Godot/Node; D = belge/öneri; — = kanıt yok.

| Sistem | C | G | Kanıt ve karar |
|---|---:|---:|---|
| İş seç → teslimat → sonuç | Kısmi | Var | C gün sonunda toplu rota simüle eder; G teklif + `dueAt` + tek ödeme uygular. Yeni MVP canlı iş modelini seçer. |
| Başlangıç kiralık araç | Market girdisi, otomatik değil | Var | C 500 EUR freelance başlar; G `v0` hazır. Yeni MVP G yönünü seçer. |
| Kendi araç/filo büyütme | Kısmi | Var | C çok araç listesi ama explicit job assignment yok; G filo ID/status/alım/satım var. |
| Araç ID/sınıf/kondisyon | Var | ID/sınıf var | C UUID + kondisyon; G stabil kayıt ID + model. Yeni MVP ikisini birleştirir. |
| Paket kapasitesi | Yalnız adet, uygulanmıyor | Adet + kg, uygulanıyor | Yeni MVP iki invariantı zorunlu uygular. |
| Kira/bakım/operasyon | Var, gömülü | Var, data-driven | Yeni MVP data-driven fiziksel gider ve ledger kullanır. |
| Araç satışı/resale | — | Var | MVP’ye alınır; kiralık satılamaz. |
| Çoklu şoför | Liste var | Var | C rota index’i ile örtük; G ayrı ID ve atama. Yeni MVP explicit assignment kullanır. |
| Hire/fire/çıkış maliyeti | Hire var, upfront yok; fire yok | Var | G: günlük maaş kadar kıdem; MVP’ye alınır. |
| Deneyim | 3 sınıf | 3 tier + hata | MVP’de veri odaklı tier, teslimat sayısı ve seeded hata bulunur. Skill tree deferred. |
| Araç başına aktif iş/paralellik | — | Var | Kritik MVP invariantı. |
| Package type/count/weight/risk | Type var; iş modeli yok | Var | MVP’de `JobOffer`/`ActiveDelivery` alanları olarak zorunlu. |
| Origin/destination/distance/reward | Route adı ve çarpan | Var | MVP’de açık alanlar; teklif kabulünde finans snapshot’ı sabitlenir. |
| Deadline/gecikme/ceza | Gün sonu Express cezası | `dueAt`, seeded delay/penalty | MVP’de `expectedAt` + `deadlineAt`; gecikme seed’li ve sınırlı. |
| Bölge/şehir/mahalle | Frankfurt/Münih/Berlin depoları | Hohenfeld veya Freiburg snapshot’ı | MVP: Almanya temalı offline şematik veri; gerçek GPS yok. |
| Günlük döngü/teklif yenileme | End Day | 24 saat pencere | MVP: günlük offer window + canlı aktif işler + günlük özet. |
| Progression gates | XP/seviye | görev/para/saat/varlık | Yeni MVP ayrı XP yerine türetilmiş şirket kademesi ve açık kapılar kullanır. |
| Tutorial | — | 3/5 adımlı | MVP: ilk iş → rota → sonuç → araç → şoför bağlamsal rehber. |
| Dashboard/cards/routes/results/targets | Kısmi dashboard/radar | Var | Compose’da ayrı ekranlar ve reusable kartlar olarak yeniden tasarlanır. |
| Settings TR/EN | Var | Var | MVP’ye alınır; Android resources/locales tercih edilir. |
| Persistence | Schema’sız Gson prefs | v6 + göç, atomik değil | Yeni versioned/atomic/validated save; doğrudan uyumluluk yok. |
| Room/Hilt/Retrofit | Yalnız GDD önerisi | — | Gereksinim değildir; MVP’ye alınmaz. |
| Background service | GDD önerisi/ticker process içi | Offline settle | Gereksinim değildir; MVP’ye alınmaz. |

## 5. MASTER FEATURE LIST

### 5.1 MVP — zorunlu ürün kapsamı

#### Ana döngü ve zaman

- Yeni oyun: `100 EUR`, başlangıç bölgesi ve stabil ID’li satılamaz kiralık panelvan.
- Her oyun günü/bölge için veri odaklı iş teklifleri; kullanılmayan teklifler bir sonraki pencereye yığılmaz.
- Teklif seçimi → uygun araç → rota → kabul → canlı kalan süre → deterministik sonuç → ledger → yeni teklif/yatırım.
- Kabul edilen iş uygulama kapalıyken duvar saatiyle ilerler; açılışta tek transaction içinde sonuçlanır. Sürekli background service gerekmez.
- Aynı teslimat yalnız bir kez ödenir; tamamlanma sırası `(dueAt, deliveryId)` ile deterministiktir.
- Günlük sabit giderler ile teslimat olayları kronolojik sırada işlenir; online/offline aynı girdide aynı state’i üretir.

#### Kariyer ve kapılar

- Sıra: kiralık araç → ilk satın alınan araç → ilk şoför → ikinci araç/paralellik → yeni bölge → depo/ileri sınıflar.
- Şirket kademesi oyuncuya özet olarak gösterilir, ancak görev/para/varlık/bölge kapılarından türetilir; ayrı XP bütçesi yoktur.
- Kapılar data-driven: minimum tamamlanan iş, para, geçen oyun zamanı, gerekli önceki bölge/araç/şoför.
- Başlangıç denge çıpası olarak Godot modelindeki 100 EUR, ilk araç için yaklaşık 8 iş ve en erken birkaç günlük kiralık dönem alınır; M2 simülasyonu rakamları kesinleştirir.

#### Filo

Her `VehicleRecord`:

- benzersiz, bir daha kullanılmayan `vehicleId`;
- `modelId`, ownership (`RENTAL/OWNED`), sınıf;
- `IDLE/ON_DELIVERY/MAINTENANCE` durumu;
- kondisyon `0..100`, kilometre/iş sayacı ve toplam kazanç;
- paket adedi ve kg kapasitesi;
- soğutma gibi capability set’i;
- tüketim, günlük bakım, alım fiyatı, resale ratio ve bölge/görev kapısı;
- `activeDeliveryId?`, `assignedDriverId?`.

MVP araç kataloğu: kiralık panelvan, hafif kurye/motosiklet, küçük panelvan, orta kamyonet, büyük kamyonet, soğutmalı kamyonet, TIR. Alım/satım, kiralık görev bedeli, bakım ve operating cost uygulanır. Kiralık başlangıç aracı satılamaz.

#### Şoförler

Her `DriverRecord`:

- benzersiz, tekrar kullanılmayan `driverId`;
- deneyim tier’i (`BEGINNER/AVERAGE/EXPERT`), işe alma bedeli, günlük maaş, hata oranı;
- teslimat/hata sayısı, uygunluk, atanmış araç;
- işe alma, boş araca atama, ayırma ve işten çıkarma;
- işten çıkarma maliyeti: data-driven kıdem, başlangıç kuralı bir günlük maaş;
- aktif teslimat üstündeki şoför doğrudan çıkarılamaz/başka araca geçirilemez.

Moral, fatigue, shift ve skill tree iyi genişleme fikirleridir ama ilk MVP’ye girmez.

#### Görev, rota ve risk

Her teklif en az:

- `offerId`, `regionId`, `originId`, `destinationId`;
- paket türü, paket adedi, paket başı/toplam kg;
- risk seviyesi, mesafe, tahmini süre, `deadlineAt`;
- temel brüt/reward, risk bonusu/ceza oranı;
- araç capability gereksinimleri;
- iki rota varsa süre/mesafe/yakıt trade-off’u.

Kabul kontrolü aynı anda:

- `packageCount <= capacityPackages`;
- `totalWeightKg <= capacityWeightKg`;
- gerekli capability (ör. refrigerated);
- araç `IDLE`;
- atanmış şoför o gün uygun/maaşlı.

Risk seed’li ve kayıtlıdır; seed oyun/iş kimliğinden türetilir. Seed’siz `Random` yoktur. Risk ödeme/gecikme/hasarı etkileyebilir ama sınırları config’te görünürdür; oyuncu ekonomisi anlamsız rastgeleliğe bırakılmaz.

#### Ekonomi

- Para birimi `Long` cent veya açık `Money` value class; `Double` ile para tutulmaz.
- `net = gross + bonus - fuel - rental - penalty`; günlük net ayrıca maaş/bakım/depo giderlerini içerir.
- Yakıt: mesafe × model tüketimi × rota çarpanı × günlük litre fiyatı.
- Kabul anında finans ve yakıt fiyatı snapshot’ı teslimata yazılır; sonuç ekranı teklif tahminiyle çelişmez.
- Ledger: brüt, yakıt, kira, personel, bakım, depo, ceza ve net ayrı kalemler.
- Satın alma/satış/hire/fire yalnız bakiye ve kapılar uygunsa atomik komutla yapılır; hiçbir komut sessiz negatif bakiye üretmez.
- Tüm katalog ve ekonomi değerleri sürümlü JSON/config kaynağından gelir; UI/ViewModel içine sayı gömülmez.

#### Almanya veri ve oyun haritası

- Katmanlar: immutable katalog (`Region`, `Location`, `RouteDefinition`) → engine mesafe/rota → Compose şematik gösterim.
- MVP, offline ve telefon öncelikli stilize Almanya ağı kullanır; gerçek GPS/navigation değildir.
- Başlangıç için Frankfurt temalı küçük, küratörlü konum seti; sonra Münih/Berlin gibi şehir kapıları düşünülebilir. İsim/lisans kontrolünden geçen mahalle/teslimat noktaları data-driven olur.
- Freiburg’daki 121 OSM konumu/14.520 yönlü çift iyi referanstır; tam grafiğin taşınması, ODbL attribution, veri kökeni, boyut ve UX doğrulanana kadar deferred’dır.
- Harita ilk MVP’de seçili/aktif rotaları, araç durumunu ve kilitli bölgeleri gösterir; gerçek zamanlı GPS araç takibi iddiası yoktur.

#### UI/UX

Telefon/portrait öncelikli ekranlar:

- Dashboard: kasa, gün, şirket kademesi, aktif işler, sonraki hedef, günlük net.
- Job Market ve iş ayrıntısı: paket/risk/kapasite, araç, rota ve net karşılaştırması.
- Active Deliveries ve result breakdown.
- Fleet ve vehicle detail/purchase/sale/maintenance.
- Drivers ve hire/assign/unassign/fire.
- Company/ledger/progression.
- Map.
- Settings: TR/EN, ses/titreşim/hareket azaltma ve save/import/reset.

Compose bileşenleri: status chip, metric card, progress/gate list, money breakdown, vehicle/driver/job card, empty/error state. Büyük listelerde `LazyColumn`; edge-to-edge/safe-area; erişilebilir content description ve minimum touch target.

Tutorial ayrı uzun ekran değil, bağlamsal akıştır: ilk teklif → rota/araç → teslimat → sonuç/para → ilk araç → ilk şoför/paralel iş. Atlanabilir ve save’e yazılır.

#### Kalıcılık

- Versiyonlu, atomik ve doğrulanan tek game snapshot; settings ayrı repository olabilir.
- Şirket, para, gün/zaman, türetilmiş progression girdileri, filo, şoförler, aktif/tamamlanmış özet işler, açık bölgeler, ledger özeti, tutorial, RNG/seed ve catalog version korunur.
- Save/load/corruption/migration iş mantığı Compose’dan ayrıdır.

### 5.2 Restorasyona değer iyi fikirler

- Compose: araç kondisyon/mileage, moral ileride, dark tycoon kart dili, sabit HUD, şematik radar, TR/EN ve açıklayıcı advisor kuralları.
- Godot/Node: başlangıç kiralık araç, fiziksel gider, araç ID/model ayrımı, iki eksenli kapasite, soğuk zincir, deterministik risk, araç başına bir iş, paralel teslimat, ledger, bölge kapıları, next target, tutorial ve motor eşitliği testleri.
- Freiburg snapshot: gerçek veri ile stilize oyun katmanını ayırma, rota seçeneğini yalnız gerçekten farklıysa gösterme, OSM sınırlarını dürüstçe belgeleme.

### 5.3 Deferred backlog

- Moral/fatigue/shift/skill tree; rastgele dünya olayları; kredi/vergi/HQ personeli; depo otomasyonu; yakıt sözleşmeleri; elektrik/şarj; borsa/rakip satın alma; reputation/müşteri memnuniyeti; achievements/seasonal events; cloud save/Google Play; premium/reklam; gerçek zamanlı araç müdahalesi; tam OSM yönlü graph; tablet/desktop özel mimari.
- Room yalnız büyük sorgulanabilir teslimat geçmişi gerçekten gerekli olduğunda.
- Hilt yalnız dependency graph manuel wiring’i aşacak ölçüde büyüdüğünde.
- Retrofit yalnız gerçek online API kararı alındığında.
- WorkManager/background service yalnız kanıtlanmış kullanıcı özelliği (örn. bildirim) için; oyunun doğruluğu bunlara bağlı olmaz.

## 6. Çatışmalar ve kesin ürün kararları

| Çatışma | Kanıt | Karar |
|---|---|---|
| `500 EUR + 75 EUR/gün + 20 XP` freelance vs başlangıç kiralık | GDD `12-14`; gereksinim `273-299`; Godot başlangıç kiralık | **100 EUR + hazır kiralık panelvan**. Freelance tıkla-kazan ve sabit XP kaldırılır. |
| XP/seviye vs görev/para/zaman kapıları | Compose `Models.kt:80-99`; Godot `economy.json:126-129`; gereksinim şirket seviyesi ister | Ayrı XP yok; şirket kademesi gerçek gate state’inden türetilir. |
| “Günü Bitir” ihale simülasyonu vs canlı rotalar | GDD `14,51-53`; Compose `startNextDay`; Godot `dueAt` | Günlük offer window + canlı aktif teslimat. “Gün sonu” yalnız özet/opsiyonel ilerletme değil. |
| Sürekli background service | GDD 2.0 önerisi `50-53` | MVP’de yok. Due işleri timestamp ile resume/foreground’da settle et. |
| Room/Hilt/Retrofit | GDD `44-48,72-75` | Öneridir, gereksinim değildir. MVP: manuel DI + atomik dosya + kotlinx.serialization; ağ yok. |
| Hohenfeld kurgu vs Freiburg OSM vs Frankfurt/Münih/Berlin | Godot kolları ve Compose depoları | MVP küçük offline Almanya-temalı/Frankfurt başlangıç kataloğu + şematik harita. Tam 121 OSM deferred. |
| Rastgele ekonomi | Compose seed’siz `Random`; gereksinim `341-351`; Godot seed’li | Seed’li ve sınırlandırılmış risk; test fixture’ları zorunlu. |
| Kasa negatif olabilir mi | Compose bunu engellemiyor; Godot eski audit negatif bakiye bulmuş | Tüm komutlarda invariant: `cash >= 0`; karşılanamayan işlem reddedilir veya açık borç modeli ayrı tasarlanır. MVP’de borç yok. |

## 7. Eski save şemaları ve uyumluluk

### 7.1 Windows Compose save

`SaveManager.kt:9-43`:

- Store: private `SharedPreferences("kargocu_save_prefs")`.
- Key `player_state`: Gson `PlayerState`.
- Key `daily_reports`: Gson `List<DailyReport>`.
- `PlayerState` alanları (`Models.kt:79-100`): `money: Double`, `currentDay`, `companyLevel`, `reputationXP`, mutable `ownedVehicles`, `hiredDrivers`, `ownedRoutes`, `isFreelancing`, `bankLoan`, `dailyLoanInstallment`, üç HQ bool, `difficulty`, `currentDepotId`, `simulationSpeed`, `language`.
- Vehicle: UUID, name, ownership, price/dailyCost, adet kapasite, kondisyon, yaş, mileage, broken, requiredLevel (`13-32`).
- Driver: UUID, ad, experience, wage, efficiency/care, morale (`35-43`).
- Route: UUID, ad, daily rent, paket aralığı, distance multiplier, level (`45-53`).
- Rapor: gün, gelir, gider, net, string events (`102-108`).
- **Şema/version yok; aktif işler, due timestamps, RNG, kg, assignment, regions/tutorial yok.**

Zayıflıklar:

- İki bağımsız JSON key aynı `apply()` editinde yazılsa da versioned envelope/checksum yok; semantic bütünlük doğrulanmıyor.
- `apply()` sonucu/hatası gözlenmiyor; last-known-good yok.
- Bozuk state sessiz `null`, bozuk rapor sessiz boş liste olur; kullanıcıya corruption bildirimi ve kurtarma yolu yok.
- Gson default/mutable alan değişikliklerinde açık migration yok.
- `Double` para ve range/reference invariantları doğrulanmıyor.

### 7.2 Godot/Node save v6

Godot `scripts/game_state.gd:899-924` alanları:

`schema_version`, `started_at`, `last_seen_at`, `window_index`, `balance`, `completed_total`, `premium_owned`, `fuel_price`, `vehicles_owned`, `fleet`, `drivers`, `warehouse`, `unlocked_regions`, `maintenance_days`, `warehouse_days`, `driver_days`, `clock_session`, `ledger`, `tutorial_step`, `last_result`, `offers`, `jobs`, `events`.

- Desteklenen sürümler v2–v6 (`12-15`); v2–v5 tekil araç/şoför/depo alanlarından filo modeline göç eder (`976-1065`).
- Filo kaydı: id/model/driver/status/bought timestamp/day/total earned (`1073-1085`).
- Şoför: id/tier/hire day/deliveries/errors/vehicle (`1088-1100`).
- Job: region/tier/type/risk/origin/destination/route/distance/packages/kg/multiplier/timestamps/delay/penalty/damage/vehicle/driver/settled (`1128-1155`).

Zayıflıklar:

- Asıl dosya `FileAccess.WRITE` ile doğrudan ezilir (`926-932`); crash aralığında atomiklik yok.
- `.bak`, bozukluk fark edildikten sonra mevcut dosyanın kopyasıdır; kesin last-known-good değildir (`934-942`).
- JSON dictionary olmak dışında zorunlu field/type/range/referential invariant kontrolü eksiktir; `saved["started_at"]` gibi doğrudan erişim crash üretebilir (`976-991`).
- Events/jobs listeleri sınırsız büyür; save boyutu ve load maliyeti artar.
- Aynı private sandbox sınırı vardır; yeni uygulama bunu kendiliğinden göremez.

### 7.3 Uyumluluk sınırı ve güvenli import

Yeni `applicationId` ayrı sandbox alır. Android, eski `com.example.kargocu` private `SharedPreferences` veya Godot `user://` dosyasını yeni uygulamaya sessizce açmaz. İmza/applicationId/backup politikası da otomatik çapraz uygulama okuması sağlamaz. Bu nedenle “kurulumdan sonra eski save otomatik bulundu” iddiası kurulamaz.

Opsiyonel importer ancak kullanıcı açıkça bir dosya sağlarsa:

1. Storage Access Framework `OpenDocument` ile kullanıcı seçimi.
2. Boyut limiti, UTF-8/JSON derinlik limiti ve timeout.
3. Kaynak türü/version tespiti; unknown future version reddi.
4. Strict parse + type/range/reference invariant doğrulaması.
5. **Dry-run özet:** para, gün, araç/şoför sayısı, kaybedilecek/varsayılanlanacak alanlar.
6. Kullanıcı onayı sonrası mevcut save’i last-known-good olarak koru.
7. Migration saf fonksiyonu → yeni state validation → tek atomik commit.
8. Hata halinde mevcut state değişmez; kişisel JSON içerik loglanmaz.

Windows eski uygulamada export özelliği yoktur; kullanıcı/ADB ile elde edilmiş gerçek dosya kanıtı bulunmadığından importer MVP Definition of Done’a zorunlu değildir.

### 7.4 Migration test matrisi

- Her desteklenen eski schema için golden fixture → beklenen yeni state.
- Eksik zorunlu alan, yanlış tip, NaN/Infinity, negatif para, out-of-range kondisyon.
- Duplicate vehicle/driver/job ID; dangling assignment; aynı araçta iki aktif iş; kapasite ihlali; settled/active çelişkisi.
- Truncated/bozuk JSON, checksum mismatch, aşırı büyük/derin input.
- Bilinmeyen eski ve ileri version.
- Migration idempotence ve save-load-save canonical round-trip.
- Temp yazım/crash, primary bozuk + last-known-good sağlam, ikisi de bozuk recovery.

## 8. Yeni mimari

### 8.1 Yalın başlangıç yapısı

M1’de tek `:app` Android modülü yeterlidir. Paket sınırları:

```text
app/
  engine/       saf Kotlin state, command, event, reducer, clock, RNG
  data/         catalog, save codec, atomic store, repositories
  feature/      dashboard, jobs, deliveries, fleet, drivers, company, map, settings
  ui/           navigation, reusable components, theme, formatting
```

Çok-modül, Room, Hilt ve network katmanı baştan eklenmez. Sınırlar testlerle ve dependency yönüyle korunur; büyüme kanıtlanırsa modül ayrılır.

### 8.2 Deterministik motor

- `GameState`: immutable data class; tüm oyun gerçekliğinin tek kaynağı.
- `GameCommand`: `AcceptJob`, `AdvanceTo`, `BuyVehicle`, `SellVehicle`, `HireDriver`, `AssignDriver`, `FireDriver`, `UnlockRegion`, vb.
- `GameEvent`: sonuç ve UI bildirimi; state’in yerine geçmez.
- `GameEngine.reduce(state, command, context): GameResult(newState, events)` saf fonksiyondur.
- `GameContext`: injected `Clock`, seeded `RandomSource`, immutable catalog/config.
- Para `Money(cents: Long)`; süre `Instant`/epoch millis ve `Duration`; ID’ler typed value class.
- İş kabulünde route, reward, cost, risk seed ve deadline snapshot’lanır; catalog değişimi aktif işi geriye dönük değiştirmez.
- Catalog startup validation: benzersiz ID, pozitif fiyat/kapasite, geçerli referans, erişilebilir başlangıç aracı/bölgesi, en az bir başlangıç işi.

### 8.3 Tek yönlü UI akışı

```text
Compose intent
  → screen ViewModel
  → application-scoped GameStore.dispatch(command)
  → engine reducer
  → immutable GameState + GameEvent
  → atomic repository snapshot
  → StateFlow projection
  → collectAsStateWithLifecycle()
```

- Ekran bazlı ViewModel ve küçük UI state projection’ları kullanılır.
- ViewModel oyun formülü içermez; UI yalnız formatlar ve intent üretir.
- Dev `GameViewModel`/`GameScreen` tekrarlanmaz.
- Navigation Compose typed route’ları ve screen-level scope kullanılır.

### 8.4 Concurrency ve paralel teslimat

- Application-scoped `GameStore` tek writer’dır; command channel/actor veya tek `Mutex` altında `dispatch → reduce → validate → save → publish` sırası çalışır.
- Her state monotonik `revision` taşır; repository eski revision yazımını reddeder.
- Autosave ve UI komutları bağımsız mutable state’e yazmaz; aynı store kuyruğuna girer.
- “Paralel teslimat” ayrı thread/yazıcı demek değildir. State içinde ayrı `ActiveDelivery(deliveryId, vehicleId, dueAt, settled=false)` kayıtlarıdır.
- Aynı araçta en fazla bir aktif iş invariantı her komutta ve load’da doğrulanır.
- `AdvanceTo(now)` tüm due işleri `(dueAt, deliveryId)` sırasıyla tek reducer transaction’ında tamamlar. `completedDeliveryIds`/settled kimliği ödeme idempotency’sini korur.
- Save snapshot immutable state’ten alınır; save sırasında state yerinde mutasyona uğramaz.

### 8.5 Repository sınırları

- `GameSaveRepository`: load/save/recover/import.
- `SettingsRepository`: dil, ses, titreşim, hareket azaltma; game state’ten ayrı ama export’a eklenebilir.
- `CatalogRepository`: bundled read-only, sürümlü JSON.
- `Clock` ve `RandomSource`: production/test implementasyonları.
- Room ancak yüksek hacimli sorgulanabilir tarih ihtiyacı doğarsa; MVP snapshot + sınırlı ledger ile dosya yeterlidir.

### 8.6 Atomik kayıt tasarımı

Envelope:

```text
formatVersion
stateVersion
catalogVersion
revision
savedAtEpochMillis
payload
payloadSha256
```

Yazım:

1. State invariant validation.
2. Canonical JSON encode + checksum.
3. Aynı dizinde `save.tmp` yaz, flush/fsync.
4. Mevcut doğrulanmış primary’yi `save.lkg` olarak koru.
5. Atomic rename/replace ile temp → primary; directory sync mümkünse uygula.
6. Başarıdan sonra revision publish et.

Yükleme:

1. Primary envelope/version/checksum/size/type/range/reference validation.
2. Başarısızsa LKG doğrula ve açık recovery event’i üret.
3. İkisi de başarısızsa dosyaları silmeden kullanıcıya new game/import/reset seçeneği göster.
4. Corruption sessizce boş state’e dönmez.

## 9. Android stack önerisi

**Erişim tarihi:** 30 Eylül 2026. “Doğrulandı” resmi sayfada stable/yayın mevcut demektir; install/build doğrulaması değildir.

| Bileşen | Arşivde gözlenen | Resmi doğrulama | M1 pin önerisi |
|---|---|---|---|
| Android Studio | IDE dosya yolu 2026.1.4 ima ediyor; kurulum çalıştırılmadı | Quail 4 `2026.1.4 Patch 1` stable sayfası | Mevcut Windows kurulumunu M1’de doğrula; gerekmedikçe IDE upgrade yok |
| AGP | `9.4.1` | AGP `9.4.0` stable; max API 37 | `9.4.0` |
| Gradle | `9.6.0` + distribution checksum | AGP 9.4 için min/default `9.6.0` | `9.6.0` + checksum |
| JDK | daemon toolchain `25`, Java source 11; kurulu olduğu kanıtlanmadı | AGP 9.4 minimum/default JDK 17 | JDK `17` toolchain; JVM target 17 |
| Kotlin + Compose plugin | `2.2.10` | Kotlin `2.4.20` stable (7 Eyl 2026) | `2.4.20`; `org.jetbrains.kotlin.plugin.compose` aynı sürüm |
| Compose BOM | `2026.02.01` | Mapping sayfasında `2026.09.00` mevcut | `2026.09.00` |
| Lifecycle | Catalog `2.6.1`, ayrıca `2.7.0` | `2.11.0` stable | `2.11.0` (`runtime-compose`, `viewmodel-compose`) |
| Navigation Compose | Yok | `2.10.2` stable; minSdk 24 | `2.10.2` |
| Activity Compose | `1.8.0` | `1.13.0` stable | `1.13.0` |
| Core | `1.19.1` | `1.19.1` stable | `1.19.1` (`core`/compat alias gerekirse) |
| Serialization JSON | Gson `2.10.1` | kotlinx.serialization `1.11.0` stable; `1.12.0-RC` stable değil | plugin `2.4.20`, JSON `1.11.0` |
| Coroutines | Transitif/açık pin yok | `1.11.0` latest stable release | `1.11.0`, test aynı sürüm |
| SDK | compile/target/min `37` | Android 17 setup: compile/target 37 mümkün; Play 31 Ağu 2026: target 36+ | `compileSdk 37`, `targetSdk 36`, `minSdk 24` |

### 9.1 Gerekçe ve doğrulama kapıları

- `minSdk 37` yalnız Android 17 cihazlarına sınırlar ve oyun API’leri bunu gerektirmez. Stable Navigation 2.10 çizgisinin minimumu API 24 olduğu için **minSdk 24** tutarlı tabandır.
- `compileSdk 37`, 2026 stable AndroidX Compose/Lifecycle artefact’larının API 37 ile derlenmesiyle uyumludur.
- **targetSdk 36**, 2026 Play şartını karşılar ve Android 17 davranış değişikliklerine M1’de zorunlu opt-in yapmaz. Target 37, M10/11 davranış testleri tamamlandıktan sonra ayrı karar olabilir.
- Firebase/Gemini/Google Services çıkarılır. Retrofit yoktur. Background service yoktur. Room/Hilt yoktur. Ücretli servis yoktur.
- `material-icons-extended` büyük artefact yerine gerekli ikonlar sınırlı kullanılmalı veya Material Symbols/vector asset seçilmelidir.
- M1 kapısı: Windows’ta tam sürümleri `./gradlew --version`, SDK Manager, dependency resolution ve Gradle Sync ile doğrula; sonra `test`, `lint`, `assembleDebug`. Bu M0’da hiçbir build geçmedi.

### 9.2 Resmi kaynaklar

- AGP 9.4 / Gradle / JDK uyumluluğu: https://developer.android.com/build/releases/agp-9-4-0-release-notes
- Gradle Java/Kotlin/Android matrix: https://docs.gradle.org/current/userguide/compatibility.html
- Kotlin releases: https://kotlinlang.org/docs/releases.html
- Compose BOM mapping: https://developer.android.com/develop/ui/compose/bom/bom-mapping
- Lifecycle: https://developer.android.com/jetpack/androidx/releases/lifecycle
- Navigation: https://developer.android.com/jetpack/androidx/releases/navigation
- Activity: https://developer.android.com/jetpack/androidx/releases/activity
- Core: https://developer.android.com/jetpack/androidx/releases/core
- Coroutines: https://github.com/Kotlin/kotlinx.coroutines/releases
- Serialization: https://github.com/Kotlin/kotlinx.serialization/releases
- Android 17 SDK: https://developer.android.com/about/versions/17/setup-sdk
- Play target API: https://developer.android.com/google/play/requirements/target-sdk
- Android Studio stable: https://developer.android.com/studio/releases

## 10. M0–M11 milestone planı

| Milestone | Çıktı | Testler | Definition of Done |
|---|---|---|---|
| **M0 Discovery** | Bu rapor; envanter; master list; kararlar; mimari/stack/save planı | 72/72 manifest hash, salt-okunur pre/post durum | Orchestrator raporu onaylar; yeni proje hâlâ yoktur |
| **M1 Foundation** | Yeni Git repo, pinned version catalog, tek app modülü, Compose shell, package sınırları, baseline engine interface | Gradle Sync, `test`, `lint`, `assembleDebug`; dependency dynamic-version denetimi | Debug APK üretilir; boş shell açılır; Windows toolchain kesin kaydedilir |
| **M2 Catalog + Economy** | Money, IDs, catalog validator, vehicle/driver/job/region/economy config, seeded offer generation | Ekonomi invariantları, katalog referansları, aynı seed aynı teklifler, 2/4/5/6 profil ilk ölçüm | Saf JVM testleri deterministik; sayı UI’da gömülü değil |
| **M3 Delivery Engine** | Accept/advance/complete; iki eksenli kapasite; capability; risk/deadline; araç başına iş; paralel işler | Paket/kg sınırı, refrigeration, seeded delay/damage, tek ödeme, paralel completion sırası, online=offline | Aynı state/commands aynı result; iki araç iki işi aynı anda taşır |
| **M4 Fleet** | Kiralık başlangıç, alım/satım, kondisyon, bakım/operasyon, resale | Yetersiz para/gate, rental satılamaz, purchase/sale round-trip, busy vehicle engeli, maintenance | Filo invariantları ve ledger kalemleri geçer |
| **M5 Drivers** | Çoklu hire/fire, tier/maaş, assign/unassign, uygunluk/deneyim | Hire/fire/çıkış maliyeti, unique ID, tek araç/şoför eşleşmesi, active-driver engeli, maaş yetmezliği | Şoför işlemleri atomik ve deterministik |
| **M6 Progression + Map** | Türetilmiş şirket kademesi, region gates, Almanya katalogu, şematik map model | Tüm gate kombinasyonları, 168 saat sınırı, location/route referansları, kilit sırası | İlk bölge erken açılmaz; GPS iddiası yok; attribution kararı kayıtlı |
| **M7 Compose Gameplay** | Dashboard, market/detail, deliveries/result, fleet, drivers, company, map, navigation, cards | ViewModel reducer contract testleri, temel Compose UI semantics, portrait preview | Dev VM/screen yok; tüm kritik komutlar UI’dan erişilebilir |
| **M8 Save + Settings + i18n** | Versioned atomic save, LKG recovery, migration/import iskeleti, TR/EN, tutorial/settings | Save/load round-trip, v fixtures, truncated/checksum/invariant corruption, process-restart, locale/tutorial | Bozuk save crash/sessiz reset yapmaz; atomic failure mevcut state’i bozmaz |
| **M9 Integration + Balance** | 14/30 günlük denge, accessibility, adaptive portrait, hata UX, performans sınırları | Kritik unit suite, lint, recomposition/list smoke, küçük/büyük telefon portrait, regression | Negatif kasa/soft-lock yok; kritik ekranlar erişilebilir ve taşmasız |
| **M10 Emulator validation** | Signed olmayan debug/test APK ve Android emulator kanıtı | Temiz kurulum; tutorial; ilk kiralık iş; rota/sonuç/para; araç/şoför/atama; ikinci araç paralel iş; save/relaunch; corruption recovery; Logcat | Gerçek emulator akışı `USER_REQUIREMENTS:893-910` geçer; crash/ANR yok; sonuç/ekran kanıtı saklanır |
| **M11 Physical device validation + polish** | Gerçek Android cihaz smoke, install/upgrade/relaunch, final regression ve release readiness raporu | M10 senaryosu fiziksel cihazda; safe-area/portrait; TR/EN/settings; offline progression; performans | Yalnız gerçekten çalıştırılan cihaz/model/OS/build raporlanır; crash varsa tamamlanmaz |

### 10.1 Kritik unit test listesi

- Ekonomi: para yuvarlama, brüt/net/ledger özdeşliği, negatif kasa yasağı, daily event order.
- Kapasite: adet, kg, capability ve sınır eşitliği.
- Filo: purchase/sale/rental/maintenance/busy vehicle/unique ID.
- Şoför: hire/fire/severance/assignment/unassignment/active-job/experience.
- Risk: seed fixture, aynı save/reload sonucu, sınır olasılıkları.
- Paralellik: N araç → en fazla N aktif iş; aynı dueAt deterministik order; idempotent completion.
- Progression: tüm gate kombinasyonları ve önceki bölge zinciri.
- Save: round-trip, canonical JSON/checksum, corruption, LKG recovery, version migrations, invariant rejection.

Bunlar planlanan testlerdir; M0’da çalıştırılmış/geçmiş sayılmaz.

## 11. Riskler, blocker’lar ve M1 giriş kriterleri

### Açık riskler

- Ekonomi sayıları üç kaynak çizgisinde farklıdır; M2’de tek config ve simülasyonla yeniden dengelenmelidir.
- Gerçek şehir/mahalle isimleri ve OSM verisi seçilirse lisans/attribution ve data provenance kapanmalıdır.
- Tam çevrimdışı duvar saatinde saat ileri alma ile gerçek beklemeyi kusursuz ayırmak mümkün değildir. MVP rekabetsiz/ücretsiz olduğundan tutarlı high-water mark + makul offline cap yeterlidir; server eklenmez.
- Eski save importer için gerçek export fixture yoktur; format bilgisi tek başına cihaz uyumluluğu kanıtı değildir.
- Önerilen 2026 stack resmi olarak mevcut olsa da Windows makinesinde resolve/build edilmedi.

### M1 giriş kriterleri

1. Orchestrator bu rapordaki MVP/backlog ve progression kararını onaylar.
2. Yeni package/application ID ve ürün adı kesinleşir; eski `kargocu`/`curier` üzerine yazılmayacağı tekrar doğrulanır.
3. Windows Android Studio/JDK 17/SDK 37/Build Tools kurulumları gerçek komutlarla doğrulanır.
4. Version catalog pinleri resmi kaynaklardan bir kez daha kontrol edilir; dynamic sürüm 0 olmalıdır.
5. M1 yalnız `C:\Users\canok\AndroidStudioProjects\kargocu-next` için başlar; eski referanslar read-only kalır.

## 12. M0 kapanış durumu

- **Üretilen:** yalnız bu analiz raporu ve `/tmp/kargocu-next-m0-report-20260930.md` kopyası.
- **Çalıştırılan test:** uygulama/build testi yok. Yalnız ZIP güvenlik kontrolü, arşiv SHA-256, manifest 72/72 doğrulaması, bounded Git metadata ve referans bütünlük karşılaştırması.
- **Final bütünlük:** beş eski Linux referansının başlangıç/son kaynak digest’i, branch, HEAD ve status’u birebir aynı kaldı; Git’siz step5 snapshot digest’i de değişmedi. İzole worktree’de beklenen tek fark `docs/kargocu-next-m0-report-20260930.md` ekidir. İki rapor kopyası aynı SHA-256’yı taşır.
- **Uygulama kodu:** oluşturulmadı.
- **APK/kurulum:** yapılmadı.
- **Alt ajan/Astra:** kullanılmadı.
- **Sonraki eylem:** orchestrator M0 incelemesi; onaydan sonra ayrı M1 görevi.
