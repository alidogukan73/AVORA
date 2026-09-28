# AVORA 3.7 stabilizasyon durumu

Son kontrol: 28 Eylül 2026. Entegrasyon dalı: `integration/phone-last-working`.
Yedek adayı kaynak sürümleri: Android `3.7.0` (80), Pi `2.12.7`, NAS `0.1.7`.
Sürüm numaraları kullanıcı isteğiyle yükseltildi. RC-6/RC-7 tamamlanmadı;
bu kayıt saha kabulünün veya mağaza yayınının tamamlandığı anlamına gelmez.
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

1. Saha adayı ve çalışan bileşen sürümleri sabitlenince RC-6'yı yeniden başlat.
   26 Eylül'de başlatılan önceki RC-6 kullanıcı tarafından iptal edildi;
   eski 29 Eylül bitiş zamanı yeni test için geçerli değildir.
   Yeni başlangıç, commit/APK özeti, servis başlangıç zamanları ve NRestarts değerlerini kaydet.
   Kesintisiz 72 saat boyunca sürüm değişirse test başlangıcını yeniden belirle.
2. RC-7: servis/log/bağlantı durumu, yetkisiz erişim reddi, güvenli aktüatör durumu,
   yedek geri dönüşü ve geçici erişimlerin kaldırılmasını doğrula.
3. Saha kabulü ve CI tamamlandıktan sonra 3.7.0 imzalı sürümünü yayımla.
   Sürüm numarası kullanıcı isteğiyle main/yedek hazırlığında önceden güncellendi.

RC-6 yeniden başlamadı; RC-7 ve 3.7 yayın onayı açık.

NAS ve Pi artık doğrulanmış Gmail uygulama şifresini kullanıyor; gizli değer Git'e
veya tanılama çıktısına yazılmadı.
