# M0 — Uygulama kabul kriterleri

Bu belge M0 incelemesi için kontrol listesidir. Kod veya test implementasyonu değildir. Son ekonomi değerleri referans karşılaştırmasından sonra belirlenecek.

## Oyun motoru ve ekonomi

- Aynı başlangıç state, komut, clock ve seed aynı sonucu verir; UI veya Android sınıfı gerektirmez.
- Para tam sayı cent ile tutulur; işlemlerde taşma, NaN ve yuvarlama belirsizliği engellenir.
- İş bedeli, risk bonusu, gider, maaş, bakım ve kira muhasebede ayrı açıklanır; UI kendi hesabını yapmaz.
- Satın alma için bakiye/seviye/araç uygunluğu doğrulanır. Satış yalnız oyuncunun sahip olduğu uygun araçta yapılır; kiralık araç satılamaz.
- Gün sonu giderleri aynı gün için iki kez tahakkuk etmez. Sonuç/ödeme kaydı yeniden açılışta tekrarlanmaz.
- Risk ve ödeme değerleri config kaynaklıdır; risk olasılığı sınırlandırılır ve seeded RNG ile test edilir.

## İşler, filo ve şoförler

- package count ve total weight birlikte kontrol edilir; count sınırında olup ağırlığı aşan iş reddedilir, tersi de reddedilir.
- Geçersiz/sıfır/negatif alanlar, bilinmeyen bölge veya araç gereksinimi, kilitli rota kontrollü hata verir.
- Aynı araç aynı anda bir aktif iş taşır. Aynı şoför iki araca atanamaz.
- İş başlangıcı uygun araç/şoför/seviye/bölge kontrolleri tamamlanmadan state değiştirmez.
- İki araçtaki bağımsız teslimatlar paralel ilerler; birinin tamamlanması diğerinin state veya parasını bozmaz.
- Aktif işteki aracı satma, şoförü çıkarma veya yeniden atama davranışı açık kurala bağlanır ve tutarlı kalır.
- Tamamlanan işe yeniden tamamlama komutu para/XP eklemez; araç ve şoför yalnız bir kez uygun hale döner.
- Şoför işe alma, maaş, işten çıkarma maliyeti ve atama referansları doğrulanır.
- Gate açılımı tekrarlanan XP/ödeme olaylarında iki kez ödül yaratmaz.

## Persistence

- saveVersion açık ve zorunludur; schema ile game state/model değişimleri ayrılır.
- Dosya yazımı atomic olur; önceki doğrulanmış kayıt beklenmedik kesintide korunur.
- Save/load karşılaştırması şirket/para/gün/progression/filo/şoför/işler/regions/tutorial/ayarlar ve gerekli zaman/RNG verilerini kapsar.
- Eksik alan, bozuk JSON, kesilmiş dosya, bilinmeyen gelecek sürüm, duplicate ID ve dangling assignment kontrollü ele alınır.
- Gelecek sürüm dosyası üzerine otomatik boş save yazılmaz. Fallback veri kaybını gizlemez.
- Birden fazla autosave isteği tek writer ile sıralanır; eski state yeni state üzerine yazılamaz.
- Migration testleri gerçek eski şema örnekleriyle yapılır; eski kaydı doğrudan değiştirerek deneme yapılmaz.
- Eski uygulamanın özel sandbox kayıtlarını otomatik okuyabilme varsayılmaz; destek varsa kullanıcı dosya import akışıyla yapılır.

## Android ve ürün doğrulaması

- M1: yeni proje, sabit sürümler/version catalog, Git ilk commit, test/lint/debug APK baseline.
- M2-M9: her milestone değişiklikle ilgili engine/persistence testleri ve gerekli compile/lint/build kontrolü; gereksiz clean yapılmaz.
- M10/M11: test + lint + assembleDebug ve cihaz/emülatör doğrulaması. Çalıştırılmayan adım geçti olarak raporlanmaz.
- Son senaryo: açılış → yeni oyun → tutorial → ilk iş → teslimat → bakiye → araç → şoför → atama → iki araçla paralel iş → save → process kapatma → yeniden açma → aynı geçerli state ile devam.
- UI: telefon öncelikli, TR/EN, dashboard/jobs/fleet/drivers/map/company/progression/settings, anlaşılır boş/hata/meşgul durumları.
- Ağır hesap ve disk yazımı main thread üzerinde yapılmaz; büyük listelerde uygun Lazy bileşenleri kullanılır.

## İşlem kontrolü

- Eski projelerde source/dependency/save değişikliği yapılmaz.
- CC izole dal/worktree kullanır; tek submit, event wait, sonuç sonrası diff ve test incelemesi.
- Her kaynak dosyası için tek writer. Review tamamlanmadan merge yapılmaz.
- Kullanıcı “dur” dediğinde aktif görev cancel edilir; yeni işlem başlatılmaz ve kaldığı nokta kaydedilir.
