# M15 — ekonomi dengelemesi

Kullanıcı önce ekonomi dengesi, ardından yapının büyütülmesini istedi ve hedef süreyi ölçümlere bıraktı. 0.1.1'in tek otomatik 100× kariyeri 129.885 saniyede kademe 4/3 bölgeye ulaştı. Bu sonucu zorunlu insan oynama süresi olarak yorumlamıyoruz; günü bitirme ayrı değerlendirilir.

## İlk ekonomi adımı

- 100 EUR, kiralık aracın maliyetsiz onarım/bakımı ve ilk şehir panelvanı 900 EUR / junior işe alımı 220 EUR korunur. İlk araç döngüsü daha geç başlamaz.
- Paket başına katkı 1.50 EUR'dan 3.50 EUR'ya çıkar; kilogram başına 0.01 EUR katkı eklenir. Yük hacmi gelirde daha anlamlıdır. Mesafe ve risk bonusları mevcut motor hesaplamasıyla devam eder; rezervler aynı tutarlı faturadan hesaplanır.
- Soğutuculu panelvan 2.800 EUR, yük kamyonu 6.000 EUR olur. Yakıt/bakım/onarım değerleri de uzman araç rolüne göre artırılır. İleride bu yatırımlar yeni kargo ve rota erişimiyle karşılık bulacaktır.
- Konfigürasyon alanları varsayılan eski davranışı taşır; kayıt state/envelope şeması değişmez. Katalog kimliği 1, eski varlık referansları için korunur. Eski kabul edilmiş işlerin faturaları yeniden fiyatlandırılmaz.

## Kanıt

85 normal motor testi geçti. Yeni kontroller: büyük/ağır iş daha fazla kazandırır ve başlangıç rezervi karşılanabilir kalır; hiçbir araç satın al-sat döngüsünde kazanç oluşturmaz; geçersiz fiyat parametreleri reddedilir; gerçek 0.1.1 emülatör kariyer kaydı yeni motorla aynı bakiye/varlık/faturalarla açılır ve devam eden işler eski kabul ödemesinden tamamlanır. Gerçek fixture `game/src/test/resources/legacy/011-endgame-save.json` içinde.

Bu ayrı denge adımıdır. Uzun kariyer, yeni kademeler/bölgeler/araçlar ve seeded gün-atlama/normal-saat karşılaştırması M16'da eklenir. Son APK ve push genişletmeden sonra hazırlanır.
