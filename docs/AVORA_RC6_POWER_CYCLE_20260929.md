# RC-6 — planlı NAS kapanışı ve otomatik toparlanma

Durum: **4 Ekim 2026 kullanıcı onayıyla RC-6 kabul edildi; dış kesinti istisnaları belgeli olarak korundu**.
Pencere: **29 Eylül 16:59–2 Ekim 16:59 Türkiye saati**.
Başlangıç UTC `2026-09-29T13:59:00Z`, bitiş UTC `2026-10-02T13:59:00Z`.
Üç sabah kontrolü: **30 Eylül, 1 Ekim, 2 Ekim en geç 10:10**.
Önceki kesintili pencere korunuyor:
[28 Eylül kaydı](AVORA_RC6_20260928.md).

Kullanıcı 29 Eylül'de ikinci seçeneği seçti: NAS'ın 23:00 kapanış / 10:00 açılış
zamanlaması korunacak; uygulama gerçek günlük çalışma düzeninde sınanacak.

## Kabul ölçütü

- Tünel onarılıp ilk sağlık kontrolleri geçtikten sonra **72 takvim saati**.
- Bu aralıkta **üç planlı sabah açılışı** gözlenecek.
- NAS için 23:00–10:00 kapalı dönem beklenen durumdur. 10:00–10:10 açılış payıdır.
- En geç **10:10'da** yerel API, HTTPS/Funnel erişimi ve Tailscale sağlıklı olmalı;
  10:10–23:00 arasında arıza veya beklenmeyen yeniden başlama inceleme gerektirir.
- Pi, MQTT ve analiz servisi tüm pencere boyunca izlenir; NAS gece kapalıyken
  Pi'deki hatalar bastırılmaz. Kaynak ve konteyner kimliği değişimi her saatte kaydedilir.
- Planlı kapanışlar NAS kayıt boşluğunu açıklayabilir; gündüz izleyici boşluğu,
  eksik sabah kaydı veya durmuş izleyici açıklanmış sayılmaz.
- Sonuç için NAS ve Pi'nin tam örnekleri, üç açılışın zamanları ve kullanıcının
  telefon gözlemi birlikte değerlendirilmeli. Yalnız sürenin dolması başarı değildir.

## Uygulanan düzeltme

Tailscale artık API konteynerinin ağ alanını paylaşmak yerine aynı Docker ağına
kendi ağ alanıyla katılıyor; proxy `http://avora-api:8787` adresini kullanıyor.
Böylece API henüz başlamadı diye tünelin Docker başlangıcı başarısız olmayacak.
Kalıcı Tailscale durumu, API kimliği, portlar ve SMTP ayarları korundu; hesap parolası değişmedi.
İki Compose kaynağı ve proxy ayarı NAS üzerinde yedeklenir; doğrulama başarısızsa
önceki yapılandırmaya geri dönülür. Yalnız tünel yeniden oluşturulur.

Kaynaklar: [Docker servis adıyla ağ erişimi](https://docs.docker.com/compose/how-tos/networking/),
[Tailscale Docker ayarları](https://tailscale.com/docs/features/containers/docker/docker-params).

NAS üzerinde mevcut **root crontab** kullanıldı; QNAP crontab yolu bu cihazda yok.
Gözlemci dakikada bir tek örnek alıyor; sayaçlar diskte korunuyor. Kurulum sonrası
zamanlayıcının ikinci örneği aldığı ve örnekte sorun olmadığı doğrulandı. Açılış
sonrası otomatik devam gerçek planlı açılışlarda ayrıca doğrulanacak. Test sonundan
iki dakika sonra veri toplamayı bırakacak. Test kapanışında yalnız kendi etiketli
cron satırı kaldırılmalı; diğer görevler korunmalı.
Pi gözlemcisi NAS yerel API ve HTTPS sağlığını ayrı okuyor; bir uç noktanın hatası
Pi servis kimliklerinin kayda alınmasını engellemiyor.

Yerel regresyon: **97 NAS testi geçti**; 9 yeni ağ geçişi / takvim politikası testi dahil.
Canlı tünel bağımsız ağda doğrulandı ve kontrollü tünel yeniden başlatma testi geçti.
Gerçek NAS sabah açılışı henüz doğrulanmadı.

## Canlı doğrulama ve kanıt

- API konteyner kimliği ve 29 Eylül 10:03:16 başlangıcı değişmedi; uygulama yeniden başlatılmadı.
- Tünel `avora_default` ağına bağımsız katılıyor, Tailscale Running; iç proxy sağlık testi başarılı.
- NAS yerel API, dış HTTPS/Funnel ve analiz sağlığı Pi üzerinden ayrı ayrı başarılı.
- Pi uygulama servisleri aynı PID ve başlangıçla çalışıyor, yeniden başlama sayaçları sıfır.
- Eski Pi gözlem süreci yalnız komutu/kimliği doğrulandıktan sonra durduruldu; eski dosyalar korunuyor.
- Yeni Pi gözlem PID: `56055`; kayıt: `/var/lib/avora-rc6/power-cycle-20260929/`.
- NAS kayıt: `/share/Docker/AVORA/rc6-power-cycle-20260929/`.
- Geçici cron etiketi: `# AVORA_RC6_POWER_CYCLE_20260929`.
- NAS yapılandırma yedeği: `/share/Docker/AVORA/backups/power-cycle-tunnel-r7g74f4c`.
- İlk ağ geçişi doğrulaması geri alınmıştı; bağlama listesi sırasının değişiklik sayılmaması
  için karşılaştırma düzeltildi. Sonraki geçiş ve tünel yeniden başlatma testi başarılı.
- NAS görevi açılış payında Docker henüz hazır değilse bunu başlangıç olayı olarak kaydeder;
  10:10 sonrası aynı durum hatadır. Pi'deki arızalar bu paydan yararlanmaz.
- Kaynak/API sürümleri değiştirilmedi: telefon kullanıcı beyanı 3.7, Pi sürüm alanı 2.12.1, NAS 0.1.6.
- [Başlangıç kaydı](rc6/20260929-power-cycle-baseline.json).
- Yerel çalışma kaydı: `.artifacts/rc6-power-cycle-20260929/run.json`.

## 2 Ekim 13:14 ara incelemesi

- Pi kaydı, yeni pencerenin başlangıcından bu kontrole kadar **4095 örnek** içeriyor;
  en büyük aralık yaklaşık 60,002 saniye, 150 saniyeyi aşan gözlem boşluğu yok.
- **30 Eylül 10:10:36–12:46:36** arasında **157 örnekte** NAS yerel API ve dış HTTPS
  erişilememiş. Her iki uçta ilk sağlıklı ölçüm **12:47:36** civarında; 10:10 kabul
  sınırı karşılanmadı. Kayıtlar erişim gecikmesini kanıtlıyor, nedenini veya fiziksel
  açılış saatini tek başına kanıtlamıyor. NAS kayıtları ve kullanıcı bağlamı gerekiyor.
- **1 Ekim 10:02:36** ve **2 Ekim 10:02:37** ölçümlerinde iki NAS sağlık ucu da
  sağlıklı; bu iki sabah ilk saat içinde 10:10 sonrası erişim hatası yok.
- Güncel Pi, MQTT ve analiz servis kimlikleri/kaynak özeti başlangıçla aynı;
  yeniden başlama sayaçları sıfır. NAS yerel ve dış sağlık uçları şu an sağlıklı.
- Bu ara kontrol NAS'ın kendi cron kayıtlarının veya boot kimliklerinin doğrulaması
  değildir. Henüz tam 72 saat dolmadı; kabul sonucu verilmedi. Test sıfırlanmadı.
- Kanıt: `.artifacts/rc6-power-cycle-20260929/pi-samples-20261002T1012Z.jsonl`
  ve `incident-20260930-late-recovery.json`.

## 2 Ekim kullanıcı açıklaması — 30 Eylül elektrik kesintisi

Kullanıcı 30 Eylül sabahındaki gecikme için **elektrik kesintisi olduğunu** bildirdi.
Olay, kullanıcı tarafından bildirilen dış güç kesintisi bağlamıyla kaydedildi.
10:10–12:46 arasındaki 157 başarısız ölçüm ve 12:47 ilk sağlıklı ölçümü korunuyor;
geçmiş kayıtlar silinmedi veya başarılıya çevrilmedi.

Kullanıcı elektrik dönüşü sonrasında **elle müdahale etmediğini** doğruladı.
Bu kullanıcı beyanı ile 12:47 ilk sağlıklı ölçümü, sistemin müdahalesiz toparlandığını
destekliyor. Kullanıcı elektriğin **yaklaşık 12:45**
saatinde döndüğünü de bildirdi. İlk sağlıklı ölçüm **12:47:36** olduğundan,
güç dönüşünden sonra toparlanma yaklaşık **2–3 dakika** olarak değerlendiriliyor.
Bu kesin süre değildir: güç dönüş saati yaklaşık, ölçümler dakikalıktır.
NAS boot/cron kayıtlarıyla toparlanma ayrıca doğrulanmalı. Bu açıklama tek başına üçüncü planlı açılış kabulünün
geçtiği anlamına gelmez; test sıfırlanmadı, bitiş zamanı değiştirilmedi.

## Sonuç

Tam kayıtlar incelendi. RC-6 koşulsuz geçmedi: üç zamanında sabah açılışı ölçütünün ikisi karşılandı; 30 Eylül dış elektrik kesintisi sonrası otomatik toparlanma doğrulandı. Ayrıca 2 Ekim test sonunda ağ erişim kesintisi var.
RC-7 ve yayın onayı açık. Test bitiminde yalnız etiketli gözlem cron görevi kaldırılmalı.

## 3 Ekim son Pi incelemesi ve telefon kabulü

- Pencere içinde **4320 örnek** var; en büyük aralık 60,002 saniye. 150 saniyeyi aşan boşluk yok. Pi, MQTT ve analiz servislerinin kimlikleri, yeniden başlama sayaçları ve kod özeti değişmedi.
- İzleyicinin 2 Ekim 17:00:37 son örneğinden sonra durması, bitişten iki dakika sonra sonlanan tasarıma uygun; yeni bir izleme arızası değil.
- Kullanıcı 29 Eylül–2 Ekim telefon kullanımında çökme, veri kaybı, ilgisiz günlük/gübre bilgisi veya yinelenen bildirim görmediğini doğruladı.
- 30 Eylül elektrik kesintisi kaydına ek olarak **2 Ekim 16:25:37–16:58:37** arasında **34 pencere içi örnekte** iki NAS sağlık ucu da erişilemezdi. Son pencere sonrası 17:00:37 örneğinde de sorun sürüyordu. Sebep ve toparlanma saati henüz bilinmiyor; önceki elektrik kesintisine otomatik bağlanmadı.
- 3 Ekim kontrolde NAS dış HTTPS ve analiz sağlıklı. NAS 192.168.1.111 yerel API ve SSH erişimi hem Windows hem Pi üzerinden başarısız; güncel NAS IP/SSH bilgisi kullanıcıdan bekleniyor.
- NAS tam samples/status kayıtları, boot/konteyner/kod kimlikleri ve cron sürekliliği henüz alınamadı. İki sabah zamanında erişim doğrulandı; 30 Eylül geç toparlanma dış güç kesintisi bağlamıyla ayrıca değerlendirilecek.
- RC-6 **geçti olarak işaretlenmedi**; test sıfırlanmadı, servis veya ayar değiştirilmedi. Yerel durum `awaiting_final_review`.
- Tam Pi kanıtı: `.artifacts/rc6-power-cycle-20260929/pi-samples-final.jsonl`; inceleme: `final-review-pending.json`; yeni olay: `incident-20261002-end-window.json`.

## 3 Ekim nihai kayıt incelemesi

- NAS aktarımındaki beş dosyanın boyut ve SHA-256 özetleri doğrulandı. **2172 pencere içi NAS örneği** ve **4320 Pi örneği** birlikte incelendi.
- NAS kaydındaki üç uzun boşluk, gece kapanışlarıyla örtüşüyor; 30 Eylül boşluğu kullanıcı tarafından bildirilen elektrik kesintisi nedeniyle 12:47'ye uzuyor. Bunun dışında 150 saniyeyi aşan gündüz örnek boşluğu yok.
- 1 ve 2 Ekim 10:02'de iki eksik NAS anlık görüntüsü açılış payı içinde; 10:03'te tam sağlıklı kayıt alınmış. Bu eksik alanlar sağlık veya kimlik değişimi sayılmadı. Pi dış erişimi iki sabah da yaklaşık 10:02:37'de doğrulamış.
- **2170 tam NAS görüntüsünde** yerel API sağlıklı, Tailscale Running; kod ve konteyner/image kimlikleri korunmuş, yeniden başlama sayaçları sıfır. Aynı boot içinde konteyner başlangıç değişimi yok.
- 30 Eylül NAS boot zamanı uptime üzerinden yaklaşık **12:44:49**, ilk tam sağlıklı NAS kaydı **12:47:01**, Pi üzerinden iki uca erişim **12:47:36**. Kullanıcının yaklaşık 12:45 güç dönüşü ve elle müdahale olmadığı beyanıyla tutarlı. Cron her açılışta kendiliğinden devam etmiş.
- **2 Ekim 16:25–test sonu** Pi'de iki NAS ucunun erişilemediği 34 ölçüme karşılık NAS'ta 34 sağlıklı örnek var. NAS uygulaması/tüneli durmamış veya yeniden başlamamış. Bu kanıt bir ağ erişim sorununa işaret eder; modem, yönlendirme, erişim filtresi veya başka bir neden henüz belirlenmedi. Kesin toparlanma saati bilinmiyor. Kullanıcıya o saatte ağ/elektrik değişikliği soruldu.
- Kullanıcı telefon gözlemi sorunsuz. Buna rağmen üç zamanında planlı açılış ve gündüz erişim sürekliliği ölçütleri tam karşılanmadığından sonuç **acceptance_not_met / completed_with_findings**. Bu bir uygulama hatası teşhisi değildir. Test otomatik sıfırlanmadı.
- Nihai kanıt özeti: `.artifacts/rc6-power-cycle-20260929/final-review.json`; NAS analizi: `nas-final-export/analysis.json`.
- Eski 72 saat takip otomasyonunun işi tamamlandı. Yeni test kararı verilmedi; geçici NAS cron satırı için ayrı temizlik planı hazır, henüz uygulanmadı. RC-7 ve yayın onayı açık.

### 2 Ekim olayı için kullanıcı doğrulaması

Kullanıcı 2 Ekim yaklaşık 16:25'te **ağ veya elektrik kesintisi/değişikliği olduğunu** doğruladı. Olay kullanıcı tarafından bildirilen dış kesinti bağlamıyla kaydedildi; tam türü, mekanizması ve dönüş saati bildirilmedi. NAS uygulamasının bu aralıkta çalıştığı kanıtı korunuyor. Bu açıklama başarısız erişim örneklerini başarılıya çevirmiyor veya üç zamanında planlı açılış ölçütünü tamamlamıyor.

## 4 Ekim kullanıcı kabulü

Kullanıcı ek testi iptal ederek RC-6'nın geçti sayılmasını açıkça istedi. RC-6 **kullanıcı onayıyla, belgelenmiş dış ağ/elektrik kesintisi istisnalarıyla kabul edildi**. Yeniden 72 saatlik test yapılmayacak. Ölçülen sonuç ve iki zamanında sabah açılışı kanıtı değiştirilmedi; bu karar üç kusursuz sabah gözlendiği iddiası değildir.

Ek test iptal kaydı: [4 Ekim ek test](AVORA_RC6_SUPPLEMENT_20261004.md). Kalan işler RC-7, geçici gözlem temizliği, mevcut değişikliklerin Git/CI kapanışı ve yayın hazırlığıdır.

4 Ekim temizlik tamamlandı: eski ve ek teste ait iki etiketli NAS cron satırı yedeklenerek kaldırıldı; diğer cron satırları korundu. Pi ek gözlemi durmuş durumda. Uygulama servisi değiştirilmedi.
