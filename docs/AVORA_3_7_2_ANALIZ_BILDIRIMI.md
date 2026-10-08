# AVORA 3.7.2 — analiz tamamlanma bildirimi

8 Ekim 2026. Android sürüm kodu 82.

Bitki Asistanı, kullanıcı tamamlanmış sonucu ekranda görmüş olsa da her kayıtta ayrı bir bildirim oluşturuyordu. Artık gelişim ve sağlık analizlerinde tamamlanmış sonucun kendi ekranında gösterilip gösterilmediği izleniyor.

- Sonuç ekranda gösterildiyse bildirim merkezine yeni kayıt ve telefon bildirimi oluşturulmaz.
- Sonuç kullanıcı ekranı arka plana aldıktan sonra geldiyse mevcut tamamlanma bildirimi korunur.
- Kullanıcı sonucu gördükten sonra kayıt işlemi bitmeden ayrılırsa gereksiz gecikmiş bildirim çıkmaz.
- Yalnız ön değerlendirmeyi görmek, henüz tamamlanmamış görsel analizin bildirimini susturmaz.
- Yeni analiz önceki analizin görülme durumunu devralmaz.

Fotoğraf, analiz, öneri ve günlük kayıtları aynı şekilde kaydedilir. Gelecekteki fotoğraf/takip hatırlatmalarına dokunulmaz. Bildirim izinleri, kategoriler ve sessiz saatler geçerlidir. İşlem Android tarafından tamamen sonlandırılırsa analizi arka planda yeniden başlatma bu değişikliğin kapsamı değildir.

Sekiz durum testi; ekranda tamamlama, arka planda tamamlama, kayıt öncesinde geri dönme/ayrılma, yeni analiz ve ekranın yeniden oluşturulması senaryolarını kapsar. Fiziksel telefon kabulü: bir analizin sonucunu ekranda bekleyin; ikinci analizde telefonun ana ekranına dönün. İlkinde bildirim çıkmaması, ikincisinde tamamlanma bildirimi gelmesi beklenir.
