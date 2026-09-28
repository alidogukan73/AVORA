# Pi geri bildirim e-postasını onarma

Bu işlem, NAS'taki Gmail uygulama şifresinin Pi'ye aktarılması için hesap sahibinin
açık onayıyla kullanılır. Canlı ortam dosyası `/etc/avora/feedback-email.env`;
parolalar komut argümanlarına, loglara veya Git'e yazılmaz.

1. Pi'de `tools/repair_feedback_email.py prepare` aracını sudo ile çalıştırın.
   JSON çıktısı aktarım kimliğini ve Base64 açık anahtarı içerir. Özel anahtar
   `/run/avora-feedback-<kimlik>/key.pem` içinde yalnız root erişimiyle tutulur.
2. NAS'ta `export_feedback_credentials.py <açık-anahtar>` aracını sudo ile
   çalıştırın. Mevcut NAS konteynerindeki Gmail kullanıcı adı ve uygulama şifresi
   RSA-OAEP/SHA-256 ile şifrelenir. `SEALED:` çıktısı yalnız şifreli veri içerir.
3. Bu şifreli veriyi Pi'deki `tools/repair_feedback_email.py apply <kimlik>`
   komutunun standart girdisine iletin. Anahtar kullanıldıktan sonra kaldırılır.

Pi aracı önce SMTP ve IMAP girişini doğrular. Güncel cihaz durumu aktif sulama veya
bekleyen manuel sulama gösterirse ayarı uygulamaz. Eski ortam dosyası 0600 izinli
bir yedeğe alınır; diğer ayarlar korunarak e-posta etkinleştirilir ve parola
güncellenir. Servis yeniden başlatıldıktan sonra yeni süreçteki ayarlar ve güncel
cihaz raporu doğrulanır. Başlatma doğrulaması başarısızsa önceki ortam dosyası
geri alınır; yeniden başlatma için tekrar boşta cihaz kontrolü gerekir.

Son aşama, Firebase'e benzersiz bir test geri bildirimi ekler; çalışan arka plan
servisinin kaydı `sent` yapmasını ve tam Message-ID'nin Gmail INBOX'ta bulunmasını
bekler. Test geri bildirimi sonunda kaldırılır; test e-postası gelen kutusunda kalır.
Teslimat aşamasındaki hata, başarıyla yüklenmiş ayarı otomatik geri almaz; sonuç
`phase=verify_feedback_delivery` olarak raporlanır ve teslimat ayrıca incelenir.

Aynı Gmail hesabına gönderimde `auto` modu mevcut `gmail_inbox` taşımasını kullanır.
Bu durumda ileti IMAP ile INBOX'a eklenir; SMTP kimlik doğrulaması ayrıca sınanır.

Yerel kontroller: `python tools/test_feedback_repair.py`,
`python tools/test_feedback_email_service.py`; NAS dizininde
`python -m unittest tests.test_feedback_export -v`.
