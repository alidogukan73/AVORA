# AVORA 3.7.5–3.7.6 — telefonda doğrulanan gübre kayıt hatası

## Kanıt ve neden

9 Ekim 2026'da USB ile bağlı Samsung SM-S928B üzerinde 3.7.4 (84) doğrulandı.
Kullanıcı gerçek gübre uygulamasını kaydetmeyi denedi. Uzun bekleyişten sonra
`MAX_RETRIES` karşılığı olan “Veriler aynı anda güncellendiği için kayıt
tamamlanamadı” mesajı doğrudan telefon ekranından okundu. Test için canlıya
uydurma bir gübre kaydı yazılmadı.

Kayıt/düzenleme/silme işlemi `/devices/avora-001` üzerinde transaction
çalıştırıyordu. Salt okunur ölçümde bu alan 10.644.417 bayt, sensör geçmişi
9.658.128 bayttı. Sensör yayını 15 saniyede bir gerçekleşiyor. Gübreyle ilgisiz
güncellemeler de tüm cihaz transaction'ının tekrar denenmesine yol açıyordu.
Önceki kurallar ve soğuk önbellek düzeltmeleri bu çakışmayı çözmüyordu.

## Değişiklik

- Android sürümü 3.7.5 (85).
- Gübre işlemleri yalnız ürün, geçmiş, plan, bölge ve sezon verilerini okur;
  sensör geçmişini, bildirimleri, komutları ve fotoğraf metadatasını taşımaz.
- Değişen alanlar tek bir atomik `updateChildren` isteğiyle gönderilir.
  Stok, kayıt ve plan birlikte kabul edilir veya birlikte reddedilir.
- `fertilizer_write_guard` altında artan revizyon, işlem kimliği ve kullanılan
  alanların önceki değerleri sunucuda doğrulanır. Değişen stok, silinen bölge,
  kapanan sezon ve eşzamanlı kayıt eski verinin üzerine yazamaz.
- En fazla beş deneme yapılır; kayıt kimlikleri denemeler arasında korunur.
  Başarı yalnız sunucu onayından sonra kullanıcıya bildirilir.
- Günlük/sezon ekleme işlemlerinin sezon `updated_at_epoch` alanını güncellediği
  mevcut sözleşme korunur. Eski istemcilerin değiştirdiği mevcut alanlar da
  önkoşullarla kontrol edilir.
- Canlı kurallar yedeklenir ve yalnız yeni guard eklenir. Canlıdaki mevcut
  sulama sınırı dahil diğer kurallar korunur.

Firebase atomik çoklu alan güncellemeleri:
[Android okuma/yazma belgesi](https://firebase.google.com/docs/database/android/read-and-write).

## Doğrulama

- 551 Android birim testi, release derlemesi ve release lint başarılı.
- Gerçek üretim mutasyonunun ürettiği JSON örneği Java testiyle sabitlenir;
  Firebase testleri aynı örneği kullanır.
- 33 Firebase kural testi başarılı. Ek kapsam: 10 MB sensör geçmişi ve sürekli
  güncellemeler, eşzamanlı kayıt, eski stok, kapanan sezon, yetkisiz kullanıcı,
  guard varken ilgisiz yazma ve eski üst alan transaction'ları.
- Eski gerçek fotoğraf/cihaz veri yapısının yerel kopyası, kontrollü gübre
  girdileri ve 10 MB sentetik sensör geçmişiyle tekrar testi başarılı.
  13.101 bayt kayıt isteği 548 ms'de doğrulandı; fotoğraf metadatası korundu.
  Bu süre yerel test ölçümüdür, telefon/ağ performans garantisi değildir.
- APK mevcut uygulamayla aynı sertifikayla imzalandı.
- 3.7.5 telefona veriler korunarak yüklendi. Kullanıcının iki denemesi de
  sunucuda doğrulandı: 1. bölge, Biohumagro, 10 ml, 9 Ekim, CONDITIONER,
  stoktan düşme kapalı. Böylece MAX_RETRIES kayıt sorununun giderildiği görüldü.
- Kullanıcı görünür onay alamadığı için kaydedilmediğini düşündü. Kullanıcının
  açık onayıyla son tekrar yedeklendi ve atomik olarak kaldırıldı; ilk kayıt
  ve stok korundu.

## 3.7.6 ekran ve sağlık puanı düzeltmesi

- Başarı mesajı geçici Toast yerine ürün, miktar, tarih, uygulama türü ve stok
  sonucunu gösteren bir onay penceresidir.
- Bölge ekranı kayıtlı uygulamaları sürekli gösterir. Önizleme, seçilen ürünün
  uygulama türündeki son/sonraki tarihe bakar.
- Aynı ürün ve aynı tarih için mevcut son kayıt yeniden girildiğinde ayrı bir
  gerçek uygulama olduğunun onayı gerekir. Bu uyarı tüm geçmişteki mükerrer
  kayıtları tarayan genel bir kontrol değildir.
- Sağlık puanı seçili plan ürününün uygulama takvimine bakar. Biohumagro
  planında kaydedilmiş toprak düzenleyici uygulaması eski genel tarihten
  kaynaklanan 10 puan kesintisini kaldırır; farklı ürünün kaydı bekleyen
  besleme gübresi uygulamasını tamamlanmış göstermez.
- Stok kutusu mevcut miktarla düşülecek miktarı karıştırmaz; yalnız girilen
  uygulama miktarının düşüleceğini açıkça söyler.
- Ürün listesi profil verisinden sonra geldiğinde eski “Kaydedilmemiş
  değişiklikler” işaretinin ekranda kalması da düzeltildi.
- 555 Android testi ve 33 kural testi başarılı. Son sürüm 3.7.6 (86).
- Fiziksel telefonda ilk gerçek kayıt günlükte görüldü; 1. bölge 100/100,
  sonraki toprak düzenleyici tarihi 24-10-2026 ve mevcut kayıt tarihi
  09-10-2026 olarak doğrulandı. 3. bölge 88/100; kalan 12 puan son analizdeki
  orta öncelikli bulguyu gösteriyor.

Gizli bilgiler içermeyen yerel kanıtlar `.artifacts/phone-fertilizer-20261009`
ve `.artifacts/fertilizer-scoped-20261009` altındadır. Özel veri kopyası Git'e
eklenmez. Önceden mevcut AGP 9.3.3 değişikliği bu düzeltmeye dahil edilmez.
