# AVORA 3.7.0 kaynak ve APK yedeği

Tarih: 28 Eylül 2026. Kullanıcı isteği: sürümleri yükselt, main'e birleştir ve yedek al.

| Bileşen | Yedek kaynak sürümü |
| --- | --- |
| Android | 3.7.0, versionCode 80 |
| Raspberry Pi | 2.12.7 |
| NAS API | 0.1.7 |
| ESP32 | 2.4.1 |
| ESP8266 | 1.3.2 |

Android sürüm kodu, önceki yedek dalındaki 79'dan büyüktür. ESP firmware kaynakları
bu entegrasyonda değişmedi. Pi/NAS sürüm değişiklikleri kaynak kodundadır; bu işlem
cihazlara yeni servis veya firmware dağıtımı yapmaz.

Telefon kabul maddeleri 1–4, kontrollü NAS parola kurtarma akışı ve Pi geri bildirim
e-postasının gerçek teslimi tamamlandı. RC-6 (72 saat) başlamadı; RC-7 ve son yayın
kabulü açık. Bu yedek, tamamlanmış saha testi veya mağaza yayını olarak değerlendirilmez.

Birleştirme öncesi main geri dönüş etiketi:
`backup/main-before-3.7.0-20260928` (`503d66e`).
Birleştirme sonrası yedek etiketi: `backup/avora-3.7.0-20260928`.

Yerel yedek dizini: `.artifacts/backups/AVORA-3.7.0-20260928/`.
Doğrulanan son commit, APK özeti ve dosya SHA-256 değerleri bu dizindeki
`manifest.json` dosyasına kaydedilir. Git bundle tüm yerel referansları ve stash'i,
kaynak ZIP'i yalnız son main'de takip edilen dosyaları içerir. İmzalı APK ayrıca saklanır.
İlk bundle sürüm değişikliğinden önceki durumu, son bundle birleşmiş main'i korur.

Gizli ayarlar, imza anahtarları, telefondaki yerel kayıtlar ve canlı NAS/Firebase
veritabanları bu kaynak/APK yedeğinin kapsamına dahil değildir. Gizli dosyalar Git'e eklenmez.

Geri dönüş için bundle doğrulaması: `git bundle verify <dosya.bundle>`.
Boş bir dizinde `git clone <dosya.bundle> AVORA-restore` ardından yedek etiketi
checkout edilerek kaynak geri alınabilir. Canlı cihaz sürümlerini geri almak ayrı
bir dağıtım işlemidir.
