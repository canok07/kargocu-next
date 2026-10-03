# M16 — ekonomi sonrası uzun kariyer / 0.2.0

Kullanıcı hedef süreyi testlere bıraktı: önce ekonomi, sonra yapı genişletmesi. M15 ilk ekonomi adımıdır; M16 erişilebilir ilk yatırımı koruyarak ilerleyen şirket için daha fazla iş, araç çeşitliliği, bölge ve yük ekler. Çalışma yerelde, CC ve alt agent olmadan yürütüldü.

## Ürün

- 4 → 8 şirket kademesi; 3 → 7 bölge; 6 → 14 merkez; 4 → 22 rota.
- Kiralık başlangıç dahil 4 → 9 araç türü; 4 → 8 yük kategorisi.
- Yeni uzman araçlar: uzun şasi, soğuk zincir, endüstriyel yük, çok amaçlı ve kıtalararası taşıyıcı.
- Elektronik, tıbbi malzeme, dökme yük ve makine; yük kategorileri şirket seviyesine göre iş pazarına gelir. Eski kabul edilmiş işlere yeni seviye engeli uygulanmaz.
- Günlük pazar şirket kademesi ve açık bölgelere göre 6'dan en fazla 24 teklife büyür; tekrar teklif oluşturmak yeni iş/para üretmez. Aynı başlangıç/bitiş ve gereksinimleri paylaşan alternatif rotalar farklı maliyet/süre sunar.
- Kademe 5–8 koşulları: sırasıyla 40/90/170/300 teslimat, 3/4/5/6 sahip olunan araç, 2/3/4/5 şoför, gün 12/18/24/32 ve 3/4/5/6 farklı sahip olunan araç türü. Bir ucuz araçtan çok almak bu çeşitlilik koşulunu geçmez.
- Harita tüm merkezleri gösterir; şirket/harita/sonraki hedeflerde çeşitlilik gereksinimi TR/EN açıklanır. Son kademe oyunu kapatmaz; iş almaya devam edilebilir.
- Application ID, mevcut kayıt şeması, önceki katalog varlık kimlikleri korunur. Yeni opsiyonel konfigürasyon alanları eski kataloglar için önceki davranışa döner. Eski aktif iş faturaları yeniden fiyatlandırılmaz.

## Süre kararının kanıtı

`careerBalanceTest`, yalnız geçerli oyun komutlarıyla, 100 EUR/kiralık araç başlangıcından beş seed'i normal takvim ve gün atlama yollarında çalıştırır. Para/araç enjeksiyonu veya üretim kataloğu değişikliği yoktur. İşler rezerv sonrası pozitif marj/süreye göre seçilir; farklı araç yatırımı, şoför atama ve servis işlemleri otomatik yapılır. Her adımda state/para ve tek ödeme kontrol edilir; boş durumda save roundtrip yapılır.

| Ölçüm | Orijinal 0.1.1 | M15, dört kademe | M16, sekiz kademe |
| --- | --- | --- | --- |
| Son aşamadaki gün | 10–11 | 11–13 | 32 |
| Tamamlanan iş | 28–33 | 36–38 | 405–423 |
| İlk araç için gereken iş | 9–11 | 8–10 | 8–9 |
| Saatin normal akış karşılığı | 3 sa 36 dk–4 sa 00 dk 29 sn | 4 sa 00 dk 29 sn–4 sa 48 dk 41 sn | 12 sa 24 dk |
| Gün atlayınca yalnız teslimat beklemesi | 4 dk 39 sn–5 dk 21 sn | 5 dk 35 sn–6 dk | 83 dk 36 sn–95 dk 08 sn |

Normal saat karşılığı oyun zamanı / 60'tır. Gün atlama değeri yalnız aktif teslimat beklemelerinin toplamıdır; menü işlemi, karar, insan dokunma süresi ve boş gün beklemelerini içermez. İkisi de garanti insan oynama süresi değildir. Önceki “3 saatte biter” çıkarımı gün atlamayı dışladığı için eksiktir. Bu nedenle uzatma yalnız gün sınırıyla yapılmadı: iş hacmi, uzman araç yatırımı ve yeni içerik de artırıldı.

Beş seed'in hepsinde son durumda altı farklı sahip olunan araç, altı şoför, yedi açık bölge ve en çok yedi paralel iş vardı. Bakiye negatif olmadı, ödeme sayısı tamamlanan iş sayısıyla aynı kaldı. Normal ve gün atlama yollarının son gün, iş ve bakiye değerleri aynıydı. Ölçümler `docs/validation/career/` içinde.

## Doğrulama

90 normal motor testi ve 20 ayrı ağır stres testi geçti. Stres: 120 seed / 240.120 komut denemesi, 165.031 uygulanan ve 75.089 reddedilen işlem, 120 deterministik tekrar; 8 seed ile 365'er günlük kariyer, 25.238 tamamlanan teslimat. Ayrıca bozuk/değiştirilmiş kayıtlar ve saat/gün sonu sınırları doğrulandı. İki kariyer testi, toplam 15 otomatik kariyer (10 gün-atlama karşılaştırması + 5 eski katalog) geçti.

Debug, test ve küçültülmüş release derlemesi geçti. Lint 0 hata / mevcut 1 OldTargetApi uyarısı. 37 Android testi ve ayrı görünür 100× ölçüm geçti: 447.339 ms, gün32/kademe8, 405 iş ve 405 benzersiz ödeme, altı farklı araç/altı şoför/yedi bölge. Son release/store sonuçları [son raporda](M16-Sonuc.md).

İlk yeni harita testinde `Freiburg Hub` tam metin seçicisi liste başındaki `• ` işaretini eşleştirmedi. Seçici şehir adını içeren öğeyi arayacak biçimde düzeltildi; harita seçimi ve çeşitlilik koşulu önceki adımlarda zaten geçmişti. İlk komut satırı test çağrısı PowerShell parametre aktarımında başarısız oldu; parametre tek argüman olarak düzeltilerek koşu başlatıldı. Bu girişimler başarılı nihai koşu yerine sayılmaz.

İlk M16 100× cihaz koşusunda motor ilerledi, fakat Compose test kare saati döngü sırasında ilerletilmediği için ekran Diğer menüsünde kaldı. Koşu 17. günde bilinçli olarak süreç durdurularak iptal edildi; bu ürünün kendiliğinden çökmesi değildir. Teste başlangıçta şirket ekranı görünürlük doğrulaması ve her döngüde `advanceTimeByFrame` / `waitForIdle` eklendi. Sadece test APK'si yeniden derlendi; üretim APK'si değişmedi. Yeniden koşuda şirket ekranının canlı güncellenmesi gerçek ekran görüntüsüyle doğrulandı. İptal edilen ölçüm nihai sonuç sayılmaz.
