# AVORA 3.7.4 — Gübre kaydı derin kontrolü

3.7.3 sonrasında kullanıcı kayıt hatasının sürdüğünü bildirdi. Önceki sentetik
veritabanı testleri eski analiz kayıtlarını ve Android'in boş cihaz önbelleğini
birlikte kapsamıyordu. Bu inceleme iki kalan engeli yeniden üretti.

## Kanıt ve neden

- Canlı kuralların önceki dağıtımla aynı olduğu doğrulandı.
- Yalnız kayıtla ilişkili alanlar salt okunur alındı; kimlik bilgileri, bildirim
  anahtarları ve özel geri bildirimler test kopyasına alınmadı. Özel test kopyası
  yalnız yerel, Git dışında `.artifacts/fertilizer-deep-20261009` dizinindedir.
- 101 fotoğraf/analiz kaydının 58'i yeni zorunlu alanlardan önce oluşturulmuştu.
  Emülatörde cihaz kökü işlemi ve yalnız günlük bölümü reddedildi; diğer dokuz
  bölüm geçti. Eski fotoğraf şemasının, ilgisiz gübre kaydını da engellediği
  doğrulandı.
- Firebase işlemi ilk denemede boş cihaz kökü verebiliyor. Önceki Android kodu
  stok ürünü bulunamadığı için sunucudaki dolu veriye ulaşamadan işlemi
  sonlandırıyordu. Stok düşümü kapalıyken de eksik kökte işlem oluşturabiliyordu.

## Düzeltme

- Var olan analiz kaydı bütün alanları aynı kalıyorsa korunabilir. Yeni veya
  değiştirilmiş kayıt, bütün güncel biçim ve değer sınırlarını karşılamalıdır.
  Bilinmeyen alanlar kabul edilmez. Eski kayıtlar silinmedi veya değiştirilmedi.
- `AtomicDeviceTransaction`, ilk boş değerde Firebase'in sunucu verisiyle
  yeniden denemesini sağlar. Gerçekte bulunmayan cihazın boş işlemini başarı
  saymaz; her yeniden denemede tamamlanma durumunu sıfırlar.
- Stok, geçmiş ve plan tarihi aynı atomik işlemde kalır. Uygulama kimlikleri
  yeniden denemeler arasında korunur. Yerel veri gözlemcilerine sunucu onayından önce geçici sonuç
  gönderilmez; başarısız kayıtta geçici stok/tarih değişikliği gösterilmez.
- Üretimde kullanılan gübre kayıt dönüşümü ayrı test edilebilir hale getirildi;
  aynı dönüşüm gerçek Firebase `MutableData` nesneleriyle sınanır.

## Doğrulama

- 553 Android birim testi, release derlemesi ve lint başarılı.

- Sekiz Android işlem testi: boş önbellek, stok düşümü açık/kapalı, iki bölge,
  stok ve tarih güncellemesi, kapalı sezon, yetersiz stok, sunucu reddi,
  eşzamanlı güncellemede tekrar deneme ve cihazın kaybolması.
- 27 Firebase güvenlik testi: eski/yeni analizler, eksik kaydı oluşturma veya
  değiştirme girişimleri, stok/kayıt işlemi ve onaylı aile erişimi dahil.
- Canlı veri kopyasında aynı işlem, düzeltmeden sonra tüm bölümler birlikteyken
  geçti. Canlı bahçeye deneme gübre kaydı yazılmadı.
- Dağıtımda yalnız `garden_journal/photo_metadata/$photoId` kuralı değişti.
  Önceden var olan canlı sulama sınırı farkı korundu. Önce/sonra yedekleri ve
  karşılaştırma sonucu yerel `deployment.json` dosyasında bulunur.
- Android sürümü 3.7.4 (84). Tam test, yayın, CI ve yedek sonuçları
  `.artifacts/release-3.7.4-20261009/release-manifest.json` içinde tutulur.
  Ayrı yerel AGP 9.3.3 değişikliği bu düzeltmenin commitine dahil edilmez.
- Telefon kabulü kullanıcı güncellemeyi kurup gerçek uygulamayı kaydettiğinde
  doğrulanabilir; yerel test sonucu telefon kabulü olarak işaretlenmez.
