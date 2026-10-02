# M13 — Son sürümün cihaz doğrulaması

2 Ekim 2026. Kullanıcının `go` talimatıyla GitHub yedeği yeniden açıldı.
Başlangıç commit: `d7d2dc40154932f4aba687d6c943e18d158b6e6b`.
CC görevi `murf695b-00afda` kredi yetersizliği nedeniyle kod/test çalıştırmadan
başarısız oldu. Kullanıcının CC kotası dolduğunda Codex'in devam etmesi yönündeki
önceki talimatı uygulanıyor. Eski referans projeler değiştirilmiyor.

## Kapsamın doğrulanması

Öğretici, TR/EN, filo, şoförler, şirket/bölge ilerlemesi, harita ve kayıt
kurtarma mevcut. Bu özellikler sırf önceki sohbet değerlendirmesinde olası
eksik olarak anıldığı için yeniden yapılmayacak. İkinci bir UI oluşturulmayacak.

## Sıra ve tamamlanma ölçütleri

1. Doğrulanmış bundle'dan aynı kaynağı aç; SDK/JBR ile APK/test APK/lint ve
   motor stres kontrolünü doğrula. Açılış düğmesiyle gerçekten yeni oyuna gir.
2. M12'de yalnız derlenen 9 kayıt/session stres testi dahil mevcut 30 Android
   testini görünür, yazılım grafik kullanan Pixel_10a'da çalıştır. Gerçek hata
   çıkarsa kök nedenini düzelt; koşulmayan testi geçti sayma.
3. Yarım kalan işlev matrisindeki araç satış ve şoför çıkarma iptal/onay
   davranışlarını, para hareketini ve yeniden açma sonucunu gerçek engine
   komutlarıyla kazanılan kariyer üzerinden doğrula. Ek regresyon yalnız
   eksik davranış kapsamı için yazılacak.
4. Son kaynağı görünür emülatörde gerçek 60 dakika izle: süreç/cihaz sağlığı,
   kayıt geçerliliği, zaman/para/aktif iş tutarlılığı ve gezinme/yeniden açma.
   Hızlandırılmış motor stresini bu testin yerine sayma. DUR gelirse kes.
5. Son APK, test kanıtları, kalan sınırlamalar ve devam notunu paketle; önceki
   GitHub yedekleme talimatı kapsamında doğrulanmış sonucu yedekle.

Fiziksel cihaz, üretim imzası ve mağaza yayını bu emülatör kontrolüyle
tamamlanmış sayılmayacak. Yeni içerik/ses sistemi/servis kapsamı eklenmeyecek.

## Son talimat ve durdurma noktası

Kullanıcının sonraki “testi bitirdikten sonra dur” talimatı bu planı daralttı.
1–3 tamamlandı; 4 (60 dakika görünür test) başlatılmadı. 5'in yerel APK ve
rapor kaydı tamamlandı, yeni GitHub push/paket yükleme yapılmadı. Son cihaz
koşusu 31/31 başarılı; ayrıntılar M13-Cihaz-Testi-Sonucu.md içindedir.
Yeni geliştirme/test için yeniden açık devam talimatı gerekir.
