# AVORA 3.7.0 — kabul sonrası Git ve yedek kapanışı

Tarih: 4 Ekim 2026. Kullanıcı son düzeltmelerin kaydedilmesini, CI doğrulamasını ve güncel yedek alınmasını istedi.

## Kapsam

- NAS Tailscale ayrı ağ alanında çalışır; API servisine `http://avora-api:8787` üzerinden ulaşır. SMTP aracı hem eski hem yeni ağ düzenini tanır.
- Güç döngüsü geçiş ve gözlem araçları ile dokuz yeni regresyon testi kaynaklara eklenir.
- RC-6, kullanıcı onayıyla belgeli dış kesinti istisnalarıyla kabul edildi. Ek test iptal edildi; geçici Pi/NAS gözlem görevleri kaldırıldı.
- RC-7 tamamlandı: servisler, erişim reddi, güvenli GPIO çıkışları, testler ve ayrı dizine yedek geri açma kontrolleri başarılı. [RC-7 raporu](AVORA_RC7_20261004.md), [makine tarafından okunabilir sonuç](rc7/20261004-summary.json).

## Yedek kapsamı ve doğrulama kaydı

Yerel yedek dizini: `.artifacts/backups/AVORA-3.7.0-20261004/`. Gerçek commit, uzak dal, CI çalışması/sonucu, dosya boyutları ve SHA-256 değerleri bu dizindeki `manifest.json` içinde kaydedilir. Bu belge tek başına CI veya yedek doğrulamasının tamamlandığını göstermez; tamamlanma kaydı manifesttir.

- Git bundle: yerel Git referansları ve korunmuş stash dahil geçmiş.
- Kaynak ZIP: kaydedilen son commitin takip edilen dosyaları.
- İmzalı Android APK: 3.7.0 / 80; önceki doğrulanmış APK korunur. Bu kapanış Android kaynaklarını değiştirmez.
- Kabul kanıtları: RC-6/RC-7 güvenli özetleri, dakika örnekleri ve CI sonuçları; gizli kurulum dosyaları alınmaz.
- Geri açma kontrolü: bundle ayrı dizine açılır, fsck ve commit karşılaştırması yapılır; ZIP CRC ve dosya özetleri doğrulanır.
- Önceki 28 Eylül yedeği değiştirilmez.

NAS veri yedeği RC-7 sırasında cihazda oluşturuldu: `/share/Docker/AVORA/backups/rc7-restore-check-20261004T142843Z` (konteynerde `/data/backups/...`). Altı SQLite veritabanı ve 51 fotoğraf ayrı kopyaya geri açılarak doğrulandı. NAS içindeki bu özel veriler Git'e veya kaynak yedeğine eklenmez.

Gizli ayarlar, imza anahtarları, canlı Firebase verileri ve telefondaki yerel veriler kaynak/APK yedeğinin kapsamında değildir. Kaynak Pi 2.12.7/NAS 0.1.7; kabul edilen canlı sürüm alanları Pi 2.12.1/NAS 0.1.6'dır. Bu işlem canlı servislere dağıtım veya mağaza yayını yapmaz.

## Geri dönüş

`git bundle verify` ile bundle doğrulanır; boş bir dizine clone edilip manifestteki commit açılır. Kaynak ZIP, APK ve kabul kanıtları manifest özetleriyle karşılaştırılır. Canlı servis geri dönüşü ve gizli ayarların geri yüklenmesi ayrı işlemlerdir.
