# AVORA 3.7.7 — arka plan bildirimleri

11 Ekim 2026, Android 3.7.7 (87).

## Bulgular

Bağlı Samsung SM-S928B üzerindeki 3.7.6 sürümünde bildirim izni açıktı;
uygulama zorla durdurulmamıştı. Pil kullanımı **Optimize** seçiliydi.
Samsung dondurma ve Android alarm kayıtları gecikmeyi doğruladı:

- 10 Ekim 08:00 hatırlatma alarmı 19:43:13'te teslim edildi.
- 11 Ekim 08:00 alarmı, uygulamanın öne gelmesiyle 11:37:50'de teslim edildi.
- Genel bildirim işi beş saate kadar yeniden deneme beklemesindeydi.

`NotificationSignalWorker`, hava durumu ve fide günlüğü sayılarını
`DataSnapshot.getValue(Number.class)` ile okuyordu. Firebase SDK bu soyut
sayı sınıfını desteklemez; dolu sayısal alan istisna oluşturur. Tek bir
okuma hatası kontrolün sonraki bölümlerini atlatır ve `Result.retry()`
nedeniyle bekleme süresini büyütür. Ön plandaki ayrı bildirim akışı
çalışırken bu arka plan kontrolü tamamlanamıyordu.

## Düzeltme

- Telefonda yalnız AVORA'nın pil ayarı **Kısıtlanmamış** yapıldı;
  sistemin uygulamaya özel muafiyet listesine girdiği doğrulandı.
- Sayısal değerler Firebase'in gerçek `Long` / `Double` değerinden okunur.
  Eksik veya geçersiz alanlar diğer bildirimleri durdurmaz.
- Eski, uzun beklemeye girmiş genel bildirim işi iptal edilir; yeni adla
  15 dakikalık kontrol başlatılır. Diğer arka plan işlerine dokunulmaz.
- Mevcut hatırlatma alarmı, paket güncellemesi ve yeniden başlatma sonrası
  genel bildirim kontrolü de başlatılır. Tek seferlik kontrol istekleri aynı
  anda yinelenmez; bildirimlerin mevcut olay bazlı tekrar koruması korunur.
- Kontrolün tamamlanması veya yalnız istisna sınıfı tanı günlüğüne yazılır;
  Firebase değerleri, hata mesajları ve kimlik bilgileri yazılmaz.

## Doğrulama

Gerçek Firebase SDK ile regresyon testi eski `Number.class` okumasının
reddedildiğini, yeni okumanın tam sayı, ondalık sayı ve zaman damgasını
işlediğini; eksik/geçersiz alanlarda kontrolü kesmediğini sınar.

Telefona tek bir açıkça etiketlenmiş test bildirimi gönderildi; uygulama
öne getirilmeden sistem bildirim kaydında görüldü. Test bahçe verisi
oluşturmadı. Pil düzeltmesinden sonra gübreleme işi yeniden normal
15 dakikalık takvimine döndü. 12:00 alarmı uygulama açılmadan 12:01:35'te
teslim edildi.

559 Android testi başarılı; imzalı sürüm derlemesi ve lint kontrolü geçti.
3.7.7 telefona veriler korunarak yüklendi. Uygulama ekranı açılmadan
12:02:18'de genel bildirim kontrolü başarıyla tamamlandı. Eski saatlerce
bekleyen iş kaldırıldı; sıradaki genel kontrol yaklaşık 15 dakika sonra.
Bu doğrulama sırasında telefon ekranı açıktı ve AVORA ön planda değildi;
uzun süreli derin uyku testi olarak sunulmaz. Kurulum ve arka plan kontrol
sonucu `.artifacts/notification-background-20261011` kanıtlarında tutulur.

Bu kontrol günler süren teslim garantisi değildir. İnternet kesintisi,
zorla durdurma veya pil ayarının yeniden kısıtlanması teslimi etkileyebilir.
Alarm kesin dakika garantisi vermez.

Kaynaklar: [Android Doze ve App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby),
[Samsung uygulama yönetimi](https://developer.samsung.com/mobile/app-management.html).
