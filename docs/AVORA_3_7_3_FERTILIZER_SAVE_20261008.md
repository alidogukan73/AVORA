# AVORA 3.7.3 — Gübreleme kaydı ve sağlık puanı açıklaması

## Sorun ve düzeltme

1. ve 3. bölgede gübreleme kaydı, stok ve sonraki uygulama tarihini birlikte
güncelleyen cihaz kökü işlemi güvenlik kurallarına takılıyordu. İki bağımsız neden
yerel Firebase emülatöründe yeniden üretildi:

- Pi tarafından işlenmiş ağ komutunda `requested=false`, eski zamanlar ve
  `acknowledged_at` bulunması, değişmeyen komutu yeni komut gibi doğrulatıyordu.
- Fide sensörü nesnesinin `newData.val() == data.val()` karşılaştırması,
  nesne içeriği korunmasına rağmen kök işlemini reddediyordu.

Değişmeyen ağ komutu ve fide ölçümleri artık tek tek alan karşılaştırmalarıyla
korunuyor. Yeni ağ komutlarının mevcut biçim, süre ve kaynak kontrolleri devam
ediyor. İstemci tamamlanmış komutu yeniden etkinleştiremiyor; sensör ölçümünü,
öneriyi veya öneri gerekçelerini değiştiremiyor. Gübre kaydı, stok düşümü ve
plan tarihi tek atomik işlem olarak kalıyor.

Fide kuralları `SeedlingTelemetry`, `SeedlingRecommendation` ve Firebase yayım
alanlarını kapsar. Gerekçeler için 16 değişmez konum ayrılmıştır; mevcut motor
en fazla beş çevresel değerlendirme üretir. Backend şemasına yeni alan
eklenirse kural şeması ve emülatör testleri de güncellenmelidir.

Android kayıt hatasında artık izin, bağlantı, stok, sezon veya eşzamanlı
güncelleme sorununu ayırt eden açıklama gösterir. Form hata sonrasında açık
kalır; bilinmeyen backend ayrıntıları kullanıcıya gösterilmez.

3. bölgedeki −12, son gelişim analizinin orta öncelikli bulgusudur; gelişim
takibinin yapılmadığı anlamına gelmez. Sağlık ekranı bunu açıklar ve öneriyi
son analiz bulgusuyla ilişkilendirir. Sağlık bulgusu ve puan hesaplaması korunur.

## Doğrulama ve dağıtım sınırı

- Android 545 birim testi: başarılı; release derlemesi ve lint: başarılı.
- Güvenlik testleri stok/kayıt/tarih atomikliğini, 1. ve 3. bölgeyi, ilk/eskimiş/
  işlenmiş ağ komutlarını ve fide alanlarının korunmasını kapsar.
- Canlı veride deneme gübre kaydı oluşturulmadı. Telefon kabulü, gerçek uygulama
  kaydının kullanıcı tarafından kaydedilmesiyle doğrulanmalıdır.
- Canlı kurallar önce `.artifacts/fertilizer-health-20261008/rules-before.json`
  dosyasına yedeklendi. Canlı manuel sulama üst sınırı 43200 saniye, depodaki
  sınır 18000 saniye: bu önceden var olan fark bu düzeltmenin kapsamı dışındadır.
  Dağıtım adayı canlı sınırı korur ve yalnız ağ komutu/fide doğrulamasını değiştirir.
  Aday testinde sulama sınırı mevcut canlı sınıra göre doğrulanır.
- Ayrı yerel AGP 9.3.3 değişikliği korunur; bu düzeltmenin commitine dahil edilmez.
- Sürüm: 3.7.3, Android yapı numarası 83. Dağıtım ve yedek kanıtları ilgili
  `.artifacts/release-3.7.3-20261008` dizininde tutulur.
