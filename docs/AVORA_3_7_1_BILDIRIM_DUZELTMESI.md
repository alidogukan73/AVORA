# AVORA 3.7.1 — gübreleme bildirimleri

8 Ekim 2026. Android sürümü 3.7.1, sürüm kodu 81.

## Doğrulanan sorun

Canlı bildirim kayıtlarında aynı bölge ve CONDITIONER önerisi için 7 Ekim öğle/akşam, ardından 8 Ekim sabah anahtarları görüldü. Açık bir uygulama tarihi bulunmayan hazır önerilerde `nextApplicationEpoch` her kontrolde şimdiki zamana atanıyordu. Gübreleme yapılmasa bile öneri ertesi gün yeniden bugüne taşınıyordu.

Gübreleme kontrolü yalnız ağ bağlantısı isteyen 15 dakikalık normal WorkManager işine bağlıydı. Telefon uyku durumunda bu iş ertelenebildiğinden bildirim uygulama açılınca görünebiliyordu. Pi günlüklerinde son yedi gün için FCM gönderim/hazırlık hatası sayısı sıfırdı; bu gübreleme hatırlatmaları zaten Pi üzerinden gönderilmiyordu.

## Değişiklik

- Açık tarihi olmayan önerinin ilk hatırlatma tarihi kalıcı tutulur. Bölge, sezon, büyüme evresi, uygulama türü, ürün ve son uygulama zamanı aynı kaldıkça tarih ilerlemez.
- Güncellemede eski hatırlatma kaydı devralınır; kurulum aynı öneriyi yeniden başlatmaz.
- Mevcut sınırlı takip düzeni korunur: ilk gün sabah/öğle/akşam, ertesi gün bir nazik hatırlatma, yedinci gün son hatırlatma. Her gün yeniden başlayan döngü kaldırılır. Gece 08:00 öncesinde takip bildirimi üretilmez.
- Yerel saatle 08:00, 12:00 ve 18:00 için uyku sırasında çalışabilen, kesin dakika izni istemeyen alarm eklenir. Alarm uygulama ekranına ihtiyaç duymadan kısa, öncelikli WorkManager kontrolü başlatır. Normal periyodik iş yedek olarak kalır.
- Yeniden başlatma, paket güncelleme ve saat/saat dilimi değişikliğinde alarm yeniden kurulur. Hatırlatmalar kapatıldığında alarm iptal edilir.
- Kontrol güncel Firebase verisini ve mevcut gübreleme güvenlik kararını kullanır. Bağlantı yoksa tekrar denenir; eski doz bilgisi alarm içine saklanmaz. Bildirim tercihleri ve sessiz saatler korunur.
- Android 8–11'de WorkManager'ın kısa ön plan uyumluluğu için sessiz kontrol bildirimi sağlanır. Kalıcı ön plan servisi kullanılmaz.

## Doğrulama ve sınırlar

Regresyon testleri: günler sonra uygulama açılışında tarihin sabit kalması, eski kayıtların devralınması, yeni sezon/ürün/uygulama döngüsü, üç günlük kontrol saati, sınırlı takip günleri ve yaz/kış saati geçişi.

Android'in kesin olmayan alarmı, iş kotası, internet erişimi ve kullanıcının uygulamaya getirdiği pil kısıtlamaları teslim saatini etkileyebilir. Zorla durdurulmuş uygulamada Android yeniden açılana kadar işleri durdurur. Dakikası dakikasına teslim garantisi verilmez.

Telefon kabulü: 3.7.1'i mevcut uygulamanın üzerine yükleyip bir kez açın. Bir sonraki hatırlatma aralığında uygulamayı ekrandan kapatıp telefonu kilitleyin; bildirimin uygulamayı açmadan geldiğini kontrol edin. Ertesi gün aynı yapılmamış öneri yeni “bugün” döngüsüne başlamamalı. Gerçek telefondaki bu gözlem otomatik testlerin yerine geçtiği varsayılmadan ayrıca alınmalıdır.

Pi/NAS servislerine, sulamaya ve üretim verisine bu düzeltme kapsamında müdahale edilmedi. Derleme sırasında ayrı olarak değişen AGP 9.3.3 ayarı bu bildirim değişikliğinin Git kapsamına alınmadı.

Resmi kaynaklar: [Android uyku ve arka plan kısıtları](https://developer.android.com/training/monitoring-device-state/doze-standby), [öncelikli WorkManager işleri](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).
