# M14 — APK tamamlama, 3 Ekim 2026

Kullanıcı: CC kullanılmadan yalnız Codex devam eder. Önce güncellemeler ve
hata düzeltmeleri, ardından son doğrulama. DUR/STOP talimatı geçerliliğini
korur. Taban commit: `4edba9b77d27dd5e3ed1956126284870b69d04f9`.

## Önce ürün düzeltmeleri

1. İş kabulü, araç alım/satımı, şoför hire/fire/assign ekranlarından yalnız
   başarılı disk commit sonrası geri dön. Hata/ret durumunda bağlamı koru.
2. Tek kullanıcı işlemi tamamlanmadan ikinci komutu kabul etme; motoru/disk
   işlemini main thread'e taşıma. Uygun işlem düğmelerinde bekleme durumu.
3. Dil, ses ve titreşim ayarlarını son committed ayarları okuyarak tek alan
   güncelle; eski UI snapshot'ının diğer ayarları geri almasını engelle.
4. Onay penceresinde tutar hesaplanmadan veya işlem mümkün değilken onay
   verme. Değişen araç/rota seçimi için eski iş önizlemesini kullanma.
5. Welcome'da başlatma düğmesini kaydırılan metnin dışında görünür tut;
   yeni oyun sıfırlamasından sonra eski ayarlar/detay ekranında kalma.
6. Büyük metinlerde fiyat/değer sütunlarının diğer içeriği dışarı itmesini
   azalt. TR/EN karşılıklarını ve mevcut özgün tasarımı koru.
7. APK sürümü 0.1.1 / versionCode2; mevcut save şeması ve ilerleme korunur.
8. Kullanıcının ek talimatıyla görünen marka Parcelrise Tycoon olur;
   paket ve yükseliş biçimli özgün vektör amblem oyunda ve ikonlarda aynıdır.
   Uygulama kimliği korunur. TR/EN mağaza metinleri, gerçek ekran görüntüleri
   ve 512px ikon / 1024x500 tanıtım görselleri kaynakla birlikte yedeklenir.

## Son doğrulama ve teslim

- Anlamlı regresyonlar: başarısız kayıt işleminde ekranın korunması,
  aynı anda gelen ayar yamaları, çift işlem, sıfırlama navigasyonu.
- Mevcut Android/engine testleri ve ilgili build/lint, görünür emülatörde
  gerçek yeniden açma/döndürme kontrolü. Koşulmayan uzun testi geçti sayma.
- Tek güncel APK, kaynak checkpoint'i, test raporu ve önceki GitHub
  yedekleme yetkisi kapsamında yedek güncellemesi.
- Referans projeler, shared SDK/araçlar ve oyuncu kayıt şeması değiştirilmez.
  Fiziksel cihaz veya üretim imzası varmış gibi rapor verilmez.
