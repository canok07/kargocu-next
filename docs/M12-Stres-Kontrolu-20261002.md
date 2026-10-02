# M12 — stres düzeltmeleri ve durdurma noktası

2 Ekim 2026. Oyun kodu db5030a9fde008b378963b2f201f5487844870da; taban ef7dced. Kullanıcının son talebi: testleri durdur, son işi bitir. Testler durduruldu; son doğrulanmış düzeltmeler paketlendi. Yeniden test başlatmak için açık devam talimatı gerekir.

## Gerçek hatalar

1. Kiralık panelvanın ücretsiz onarım/bakımı sıfır tutarlı ledger kaydı nedeniyle invariant denetiminde reddediliyordu. Sıfır maliyetli işlemler artık ledger satırı oluşturmadan kondisyon/bakım çıpasını günceller. Ücretli işlemlerin muhasebesi korunur. İki eski-başarısız regresyon ve bir ücretli onarım regresyonu eklendi. Görünür cihazda 454 km'ye ulaşmış kiralık araç bakımının önce başarısız, yeni APK'da başarılı olduğu doğrulandı; bakiye aynı kaldı ve ikinci bakım düğmesi devre dışı kaldı.
2. 2 MiB sınırı içindeki 200.000 seviyelik bozuk JSON ayrıştırması StackOverflowError atıyordu. Ayrıştırma ve state deserialization hatası kontrollü Corrupt(InvalidJson) sonucuna çevrilir. Ağır testte örnek artık uygulama dışına hata fırlatmadı.

## Doğrulananlar

- Windows: 81 normal JVM testi; ayrı 20 stres testi; başarısız test yok. Eski 78 test değiştirilmedi, 3 regresyon eklendi.
- Aynı 120 seed × 2.000 komut + seed başına ilk teklif üretimi: 240.120 deneme; 152.698 uygulanmış, 87.422 reddedilmiş komut. 120 replay birebir aynı state. 31.855 tamamlanan iş. Windows motor kampanyası 38.634 ms.
- 8 seed × 365 oyun günü: 14.440 iş; üç satın alınabilir araç modeli ve tüm bölgeler; en çok dört paralel iş. En büyük ledger 256, en büyük kayıt 100.445 byte.
- 4.096 byte varyantının 4.091'i bozuk olarak reddedildi; checksum harfinin büyüklüğünü değiştiren 5 zararsız varyant aynı state ile kabul edildi. Checksum'u yeniden hesaplanmış 2.048 geçersiz-state varyantı reddedildi. Ek 256 primary/backup aday kümesi, zaman/numerik/gelecek sürüm/sınır kontrolleri geçti.
- Debug, Android test APK, release küçültme ve lint Windows'ta geçti. Bu toplam derleme 1 dakika 33 saniye sürdü.
- Görünür cihazda önceki oyun koduyla gerçek kazanç → araç alımı → Ada'yı işe alma/atama → iki eşzamanlı teslimat kariyeri geçti. Ücretli onarım 55 cent, onarım iptali, erken bakım engeli, atamalı araç satış engeli ve kiralık araç koruması doğrulandı.
- Yeni kodla ücretsiz kiralık bakım/tekrar engeli, iki taraflı atama kaldırma, işten çıkarma iptali ve tek 950-cent tazminat doğrulandı.
- Önceki ayrı yazılım grafikli emülatör koşusu: 1.000 gezinme dokunuşu, 40 gözlenen döndürme, 30 gerçek süreç kapatma/açma; idle kayıt/bakiye/filo/şoförler/ayarlar korundu. Bu koşu M11 kodundaydı ve sonraki görünür oturumdan önce tamamlandı.

## Tamamlanmayanlar

- Yeni StressAndroidSaveRepositoryTest ve StressSessionControllerTest içindeki 9 Android testi derlendi; cihazda çalıştırılmadı.
- Son görünür işlev akışı işten çıkarmadan sonra cihazın kaybolmasıyla kesildi. Araç satışının iptal/onay adımı ve sonraki ayar/harita/kurtarma matrisi bu koşuda tamamlanmadı. Bağlantı kaybının sebebi belirlenmedi; oyun çökmesi olarak varsayılmadı.
- Gerçek 60 dakikalık görünür test için izleme hazırlandı ama test başlatılmadı. Hızlandırılmış 365 günlük motor koşusu bu gerçek süre testinin yerine sayılmaz.
- İlk donanım grafik gezinme koşusu Windows NVIDIA nvoglv64.dll emülatör hatasıyla yarıda kaldı ve başarılı sayılmadı. Yazılım grafikli ayrı cihazda önceki gezinme koşusu geçti; fiziksel cihaz performansı hakkında iddia yok.

CC görevi mur575sh-abf60e, DeepSeek V4.1 Flash; tek submit ve event wait. Kaynak paket SHA256: 0438e1276ffdcc44b2ee7b999ad7332702111582ea36109749db6e58c053a050. Çıktı önce incelendi, sonra ayrı Windows deneme dalında doğrulandı. CC özetindeki 151.698 sayısı yazım hatasıdır; makine çıktısı ve bağımsız Windows tekrarı 152.698 uygulama verir. CC'nin otomatik taşıyıcı proje kontrolündeki tek test oyun testi sayılmadı.

Üretim imzası, fiziksel cihaz testi ve mağaza yayını yoktur. Son APK bir geliştirme paketidir. Eski projeler ve kullanıcının ana oyun kayıtları değiştirilmedi.
