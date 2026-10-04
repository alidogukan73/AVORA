# AVORA 3.7 stabilizasyon durumu

Son kontrol: 4 Ekim 2026. Güncel dal: `main`; PR #14 birleştirildi.
Yedek adayı kaynak sürümleri: Android `3.7.0` (80), Pi `2.12.7`, NAS `0.1.7`.
Sürüm numaraları kullanıcı isteğiyle yükseltildi. RC-6, 4 Ekim kullanıcı onayıyla belgeli dış kesinti istisnalarıyla kabul edildi. RC-7, 4 Ekim son kontrolleriyle tamamlandı; Git/CI, güncel yedek ve yayın kapanışı açık.
Pi ve NAS kaynak sürümlerinin yükseltilmesi canlı servislere dağıtım yapmaz.

## Tamamlanan çalışmalar

- A–F temel stabilizasyon: referans sürüm, depo temizliği, CI, ağ güvenliği,
  sulama güvenliği ve yedek içerik bütünlüğü. Main tabanı: `503d66e`.
- NAS iki yönlü eşitleme, cihaz yetki isteği ve günlük alt menüsü entegrasyonu.
- Günlükte Gözlem / Önemli gelişme, beş gelişme alt türü, isteğe bağlı fotoğraf;
  form yeniden oluşturulunca seçimlerin korunması ve boş açıklama kontrolü.
- Bağlantısı doğrulanamayan veya sağlık raporu eski Pi için sağlıklı gösteriminin kaldırılması.
- NAS yönetici girişinden kalıcı Firebase kimliğiyle bahçe yetkisinin geri alınması.
  27 Eylül dağıtım kaydı iki bağımsız girişte aynı kimliği ve altı bölgenin okunmasını doğruluyor.
- Şifre sıfırlama kodu, testleri ve canlı SMTP kurulumu tamamlandı.
  28 Eylül 16:08'de Gmail kimlik doğrulaması, test e-postasının gelen kutusunda
  bulunması ve Portainer kaydının güncellenmesi doğrulandı. Hesap parolası değiştirilmedi.
  NAS yedeği: `/share/Docker/AVORA/backups/smtp-update-20260928-160754-k192smth`.
  Kullanıcı, telefonun Şifremi unuttum ekranından istediği gerçek kurtarma kodunun
  e-postaya geldiğini de doğruladı. Kodun kullanılıp hesap parolasının değiştirildiği
  canlı kullanıcı hesabında ayrıca doğrulanmadı; kontrollü hesap kabulü aşağıda tamamlandı.
- Eski API ağ alanına bağlı kalıp 128 koduyla durmuş Tailscale tüneli onarıldı.
  İlk onarımda API kimliği ve sağlığı korundu. SMTP uygulanırken tünel önce
  kaldırılıp API'den sonra oluşturuldu; kalıcı veri birimleri silinmedi.
- Entegrasyon dalı GitHub'a gönderildi; taslak PR #14 açıldı.
  `ea4855d` için Android, NAS, Pi ve Firebase CI işleri başarılı.
  SMTP aracı dahil `1fff415` için AVORA CI #35 de başarılı.
  Günlük ilişki düzeltmesi `bdf4f0e` için AVORA CI #36 başarılı.
- Gübre ekranındaki toplu uygulama butonunun hizalanması.
- Analiz ve tamamlanan takip için tek bildirim: `9af1efe`.
  Eski stash silinmedi; ESP32/ESP8266 MQTT kimlik doğrulama değişiklikleri güncel kodda zaten var.
- Pi geri bildirim e-postası yeniden etkinleştirildi. Kullanıcının açık onayıyla
  NAS'taki çalışan Gmail uygulama şifresi Pi'ye RSA-OAEP ile şifreli aktarıldı.
  Eski 535 kimlik doğrulama hatası giderildi; SMTP ve IMAP girişleri başarılı.

## Doğrulama kanıtları

- 28 Eylül: Günlük ilişki düzeltmesi sonrası Android `testDebugUnitTest` başarılı;
  526 test, 0 hata (6 yeni ilişki filtresi regresyon testi dahil).
- 28 Eylül: `assembleDebug` ve `assembleDebugAndroidTest` başarılı.
  Temiz Android emülatörü ve yerel Firebase üzerinde `JournalRecordPersistenceTest`
  3/3 geçti: boş açıklama, fotoğraflı gelişme ve fotoğrafsız gözlem. Aynı bölge ve
  sezonda ilgisiz gübre kaydı varken detayın bu veriyi aldığı beklendi; kayıt
  açıklaması/fotoğrafları doğru, ilişkili kayıt bölümü gizli doğrulandı.
- Telefon kabul hazırlığı: `assembleRelease` başarılı; APK v2 imzası doğrulandı.
  Release paket: `com.alidogukan.avora`, sürüm `3.6.12` (75).
  Günlük düzeltmesini içeren APK SHA-256:
  `B6C226B2FE80BD3E3170D58E77C783EDB7EA22A1D8A7F277D9E3B9BF567D308B`.
  Kullanıcı telefon kontrollerini kendisi yaptı: 1 (yeniden açılış/veri erişimi),
  3 (çevrimdışı sağlık ve yeniden bağlantı), 4 (tek analiz/takip bildirimi) geçti.
  2 (günlük kaydı) sırasında ilgisiz gübreleme bilgisi bildirildi. Detay ekranı
  aynı bölgedeki ilk iki gübreleme/sulama kaydını bağlantı olmadan gösteriyordu.
  Bu seçim kaldırıldı; yalnız fotoğrafta açık uygulama kimliği bulunan, aynı bölge
  ve sezona ait gübreleme gösteriliyor. Bağlantı yoksa ilişkili kayıt bölümü gizli.
  Kullanıcı düzeltmeden sonra 2. maddeyi telefonda yeniden kontrol edip geçtiğini
  bildirdi. Böylece telefon kabul maddeleri 1, 2, 3 ve 4 tamamlandı.
- 28 Eylül: NAS unittest paketi 72/72 başarılı.
- SMTP kurulum aracının 11 yerel testi başarılı: yalnız tünel onarımı, güvenli
  başlatma sırası, yapılandırma koruması, hata/iptal geri dönüşü ve tanılama gizliliği.
- 28 Eylül 18:02: NAS konteynerinde kurulu kod ve mevcut Gmail ayarlarıyla kontrollü
  kurtarma kabul testi başarılı. Loopback HTTP sunucusu ve ayrı geçici veritabanındaki
  test hesabı için gerçek e-posta teslimi, kodla parola değişimi, yeni parolayla giriş,
  aynı kodun ikinci kullanımının reddi, eski parolanın ve iki eski oturumun reddi
  doğrulandı. Geçici veriler kaldırıldı; mevcut API kimliği/sağlığı değişmedi.
  Canlı kullanıcı hesabının parolası değiştirilmedi. Araç: `NasServer/verify_password_recovery.py`.
  Aracın üç yerel testi ve mevcut 12 kurtarma/HTTP testi başarılı.
- 28 Eylül: Pi süre politikası, röle zaman aşımı, yeniden başlatma güvenliği ve
  çevrimdışı komut politikası yerel testleri başarılı; gerçek donanım testi değildir.
- 28 Eylül 18:31: Pi arka plan servisi kontrollü geri bildirimi `sent` olarak işaretledi;
  tam Message-ID Gmail INBOX'ta doğrulandı. Aynı hesaba teslim için mevcut
  `gmail_inbox` modu kullanılıyor. Test geri bildirimi Firebase'den temizlendi;
  kuyrukta bekleyen bir eski geri bildirim de teslim edildi.
  Aktif sulama kontrolünden sonra servis 18:29:49'da yeniden başlatıldı; son kontrolde
  `active/running`, PID `16084`, `NRestarts=0`. Ortam dosyası izni `600`; tek kullanımlık
  aktarım anahtarı kaldırıldı. Yedek:
  `/etc/avora/feedback-email.env.before-repair-20260928-182948`.
  Onarım korumaları için 3 test, şifreli aktarım için 2 test ve mevcut geri bildirim
  teslimat regresyon betiği başarılı.
- Önceki çalışma kayıtları günlük, sağlık ve kurtarma emülatör testlerini başarılı bildiriyor.
- RC-1–RC-5 ve gerçek yedek/geri yükleme denemeleri önceki sohbet kayıtlarında başarılı;
  yeni aday için gereken saha kontrolleri tekrar doğrulanmalıdır.

## Açık işler ve sıra

RC-6 kullanıcı onayıyla kabul edildi; yeni 72 saatlik ek test iptal edildi. Önceki kesinti kanıtları korunuyor. [RC-6 sonuç ve kullanıcı kabulü](AVORA_RC6_POWER_CYCLE_20260929.md).

Geçici gözlem temizliği tamamlandı: Pi ek gözlem süreci durduruldu; eski ve ek test NAS cron satırları yedeklenerek kaldırıldı. Diğer görevler korundu.

**RC-7 tamamlandı:** servis/bağlantı, erişim reddi, güvenli GPIO çıkışları, 97 NAS testi, 9 Pi regresyon betiği, kaynak ve gerçek NAS verilerinin ayrı dizine geri açılması, geçici erişim temizliği doğrulandı. Üç geçmiş sensör veri uyarısında güncel toparlanma doğrulandı. [RC-7 raporu](AVORA_RC7_20261004.md).
1. **Git/CI kapanışı:** NAS tünel düzeltmesi, yardımcı araçlar, testler ve güncel kabul belgelerinin çalışma ağacındaki değişikliklerini gözden geçirip kaydetme; son durum için CI doğrulama ve güncel yedek. Önceki main/yedek korunuyor.
2. **3.7 yayın hazırlığı:** imzalı son paket ve yayın notları, kabul edilen canlı sürüm bileşiminin kaydı ve dağıtım/yayın onayı. Kaynak Pi 2.12.7/NAS 0.1.7; canlı alanlar Pi 2.12.1/NAS 0.1.6. Bu kaynak sürüm artışları henüz canlıya dağıtılmadı. Yeni dağıtım bu kabul kararıyla yapılmış sayılmaz.

## Önceki test olayları (tarihsel kayıt)

RC-6 kesintiye uğradı: NAS 28 Eylül 23:01'de durmuş, 29 Eylül 10:01 civarında
yeniden açılmış. API sağlıklı; Tailscale tüneli ağ alanı hatasıyla kapalı, NAS izleyicisi
durmuş. Mevcut pencere kesintisiz 72 saat kabulünü karşılamıyor; onarım ve yeni
başlangıç değerlendirmesi gerekiyor. Otomatik onarım veya test sıfırlaması yapılmadı.
RC-7 ve 3.7 yayın onayı açık.

NAS ve Pi artık doğrulanmış Gmail uygulama şifresini kullanıyor; gizli değer Git'e
veya tanılama çıktısına yazılmadı.

29 Eylül: Kullanıcı planlı NAS güç döngüsü kabulünü seçti. Yeni pencere kurulum sonrası
başlayacak; [hazırlık ve kabul ölçütleri](AVORA_RC6_POWER_CYCLE_20260929.md).

29 Eylül 16:59: Güç zamanlaması korunarak yeni RC-6 penceresi **2 Ekim 16:59** bitişli
olarak kuruldu. Kalıcı tünel ağ düzeltmesi ve kontrollü tünel yeniden başlatma doğrulandı.
NAS root cron gözlem görevi ölçüm aldı; Pi gözlemi yeni ölçüte geçti.
Üç gerçek sabah açılışı ve telefon saha gözlemi bekleniyor. 97 NAS testi başarılı.

4 Ekim: Kullanıcı yeni 72 saatlik testi iptal etti ve RC-6'yı geçti kabul etti. Bu karar yukarıdaki tarihsel açık/başarısız RC-6 durumlarının güncel kabul durumunu değiştirir; orijinal ölçümler korunur. RC-7 ve yayın işleri açık kalır.

4 Ekim RC-7 tamamlandı. Yukarıdaki tarihsel RC-7 açık notlarının güncel karşılığı [RC-7 raporudur](AVORA_RC7_20261004.md). Git/CI, son yedek ve yayın hazırlığı açık.

4 Ekim Git/CI ve güncel yedek kapanışı: [kapsam ve doğrulama kaydı](AVORA_3_7_0_KAPANIS_YEDEGI_20261004.md). Sonuç, commit ve CI bağlantısı yerel yedek manifestinde tutulur; paket/dağıtım yayını ayrı kalır.
