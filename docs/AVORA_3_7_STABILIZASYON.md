# AVORA 3.7 stabilizasyon durumu

Son kontrol: 28 Eylül 2026. Entegrasyon dalı: `integration/phone-last-working`.
Android sürümü henüz `3.6.12` (75); bu belge 3.7 yayımlandığı anlamına gelmez.

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
- Eski API ağ alanına bağlı kalıp 128 koduyla durmuş Tailscale tüneli onarıldı.
  İlk onarımda API kimliği ve sağlığı korundu. SMTP uygulanırken tünel önce
  kaldırılıp API'den sonra oluşturuldu; kalıcı veri birimleri silinmedi.
- Entegrasyon dalı GitHub'a gönderildi; taslak PR #14 açıldı.
  `ea4855d` için Android, NAS, Pi ve Firebase CI işleri başarılı.
- Gübre ekranındaki toplu uygulama butonunun hizalanması.
- Analiz ve tamamlanan takip için tek bildirim: `9af1efe`.
  Eski stash silinmedi; ESP32/ESP8266 MQTT kimlik doğrulama değişiklikleri güncel kodda zaten var.

## Doğrulama kanıtları

- 28 Eylül: Android `testDebugUnitTest` yeniden çalıştı; 520 test, 0 hata.
- 28 Eylül: `assembleDebug` ve `assembleDebugAndroidTest` başarılı.
  Ekran testi APK'sının derlenmesi, cihazda testlerin çalıştırıldığı anlamına gelmez.
- 28 Eylül: NAS unittest paketi 72/72 başarılı.
- SMTP kurulum aracının 11 yerel testi başarılı: yalnız tünel onarımı, güvenli
  başlatma sırası, yapılandırma koruması, hata/iptal geri dönüşü ve tanılama gizliliği.
- 28 Eylül: Pi süre politikası, röle zaman aşımı, yeniden başlatma güvenliği ve
  çevrimdışı komut politikası yerel testleri başarılı; gerçek donanım testi değildir.
- Önceki çalışma kayıtları günlük, sağlık ve kurtarma emülatör testlerini başarılı bildiriyor.
- RC-1–RC-5 ve gerçek yedek/geri yükleme denemeleri önceki sohbet kayıtlarında başarılı;
  yeni aday için gereken saha kontrolleri tekrar doğrulanmalıdır.

## Açık işler ve sıra

1. Yeni SMTP kurulum aracı ve canlı doğrulama kaydını Git'e kaydet; güncel CI'ı izle.
2. Telefonun Şifremi unuttum ekranından gerçek kurtarma kodunu istemeyi ve kontrollü
   hesapta tek kullanımlık kodla sıfırlamayı doğrula. SMTP test mesajının teslimi
   tamamlandı; bu henüz uçtan uca hesap sıfırlama testi değildir.
3. Fiziksel telefonda yönetici girişi, verilerin gelmesi, günlükte fotoğraflı/fotoğrafsız
   kayıt, cihaz sağlığı ve tek bildirim davranışını doğrula.
   28 Eylül kontrolünde ADB'ye bağlı cihaz yoktu. Veri yedeği olmadan uygulamayı silme.
4. Saha adayı ve çalışan bileşen sürümleri sabitlenince RC-6'yı yeniden başlat.
   26 Eylül'de başlatılan önceki RC-6 kullanıcı tarafından iptal edildi;
   eski 29 Eylül bitiş zamanı yeni test için geçerli değildir.
   Yeni başlangıç, commit/APK özeti, servis başlangıç zamanları ve NRestarts değerlerini kaydet.
   Kesintisiz 72 saat boyunca sürüm değişirse test başlangıcını yeniden belirle.
5. RC-7: servis/log/bağlantı durumu, yetkisiz erişim reddi, güvenli aktüatör durumu,
   yedek geri dönüşü ve geçici erişimlerin kaldırılmasını doğrula.
6. Saha kabulü ve CI tamamlandıktan sonra sürüm numarasını 3.7 olarak güncelle,
   imzalı sürümü doğrula ve yayımla.

RC-6 yeniden başlamadı; RC-7 ve 3.7 yayın onayı açık.

Not: NAS için yeni Gmail uygulama şifresi kullanıldı. Pi'deki eski geri bildirim
e-posta şifresi bu işlemde değiştirilmedi; önceki SMTP giriş denemeleri 535 hatası
vermişti. Pi geri bildirim teslimatı ayrıca kontrol edilmeli.
