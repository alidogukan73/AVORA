# RC-6 ek doğrulama — 4 Ekim 2026

Durum: **4 Ekim 2026 kullanıcı talebiyle iptal edildi**. İki cihazın kurulumu tamamlanmış ve 4 Ekim 17:14–7 Ekim 17:14 pencere zamanı hazırlanmıştı. Bu pencere için ek test kabulü aranmayacak; eski RC-6 kullanıcı onayıyla istisnalı kabul edildi. Pi gözlem PID 231450, tam komutu doğrulanarak durduruldu. NAS geçici görevlerini kaldırmak için giriş penceresi açıldı; sonuç aşağıda veya yerel run.json içinde doğrulanır. Etkin ek-test otomasyonu bulunmadı. Eski örnekler korunur.


## Amaç ve kabul koşulları

Kullanıcı ek testle doğrulamayı onayladı. Önceki testin kanıtları korunur; önceki başarısız erişim örnekleri değiştirilmez. Aynı kabul koşullarıyla yeni, bağımsız **72 takvim saati** ve **üç sabah açılışı** izlenir. 4 Ekim kurulumu tamamlanırsa sabahlar 5, 6 ve 7 Ekim olacaktır.

- NAS 23:00–10:00 kapalı; 10:00–10:10 açılış payı. Her sabah en geç 10:10'da yerel API, HTTPS/Funnel ve Tailscale sağlıklı olmalı; 10:10–23:00 erişim sürmeli.
- Pi, MQTT ve analiz her saatte sağlıklı kalmalı. Pi'de 150 saniyeyi aşan örnek boşluğu olmamalı.
- NAS gündüz kayıtları tam olmalı; yalnız planlı gece boşlukları beklenir. Açılış payındaki eksik alanlar kimlik değişimi sayılmaz.
- Kod, sürüm, konteyner kimlikleri korunmalı. NAS planlı boot değişimleri beklenir; gündüz beklenmeyen restart kabul edilmez.
- Kesinti veya değişiklik olursa olay kaydedilir; test otomatik sıfırlanmaz ve eski sonuç silinmez.
- Süre bitiminde her iki cihazın tam örnekleri ve kullanıcı telefon gözlemiyle değerlendirilir. Yalnız süre dolması başarı değildir.

## Kurulum sınırları

Uygulama servisi, aktüatör, sürüm veya güç takvimi değiştirilmez. Öncekiyle aynı gözlemci kodu kullanılır. NAS root crontab yedeği alınarak yalnız yeni geçici gözlem satırı eklenir; mevcut satırlar korunur. NAS'ta cron'un ikinci örneği aldığı doğrulanır; Pi'de süreç ve ilk sağlıklı örnek doğrulanır. Ortak başlangıç NAS kurulumu sırasında yaklaşık altı dakika ileriye seçilir; Pi kurulumu bu tarihe yetişmezse test başladı sayılmaz.

- Yerel kayıt: `.artifacts/rc6-supplement-20261004/run.json`.
- NAS: `/share/Docker/AVORA/rc6-supplement-20261004`.
- Pi: `/var/lib/avora-rc6/supplement-20261004`.
- Yeni cron etiketi: `# AVORA_RC6_SUPPLEMENT_20261004`.
- Eski cron etiketi korunur: `# AVORA_RC6_POWER_CYCLE_20260929`; eski gözlemci süresi dolduğundan veri toplamaz. İki etiket için temizlik ayrıca yapılmalı.
- Gözlemciler yeni bitişten iki dakika sonra veri toplamayı bırakır. Sonrasında duran Pi süreci/eski son örnek yeni arıza sayılmaz.
- Eski rapor: [RC-6 ilk güç döngüsü testi](AVORA_RC6_POWER_CYCLE_20260929.md).

4 Ekim temizlik tamamlandı: eski ve ek teste ait iki etiketli NAS cron satırı yedeklenerek kaldırıldı; diğer cron satırları korundu. Pi ek gözlemi durmuş durumda. Uygulama servisi değiştirilmedi.
