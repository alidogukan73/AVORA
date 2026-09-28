# AVORA NAS Sunucusu

Bu paket, Firebase'in yanında bağımsız olarak çalışacak hafif AVORA sunucu temelidir.
Android uygulamasındaki Veri Eşitleme ekranı NAS servisinin durumunu denetleyebilir ve
isteğe bağlı NAS hesabı açabilir. Bahçe verileri ve Raspberry Pi akışı şimdilik Firebase'i
kullanmaya devam eder; mevcut çalışma düzeni değişmez.

## Tasarım

- Hesap/veri API'si Python standart kütüphanesini kullanır. Kalıcı yönetici Firebase
  oturumu için `requirements.txt` içindeki Firebase Admin SDK kullanılır.
- Hesap ve oturum bilgileri `database/accounts.sqlite3` içinde tutulur.
- Her kullanıcının verisi `database/users/<kullanıcı-kimliği>.sqlite3` adlı ayrı bir
  SQLite dosyasındadır.
- Her kullanıcının fotoğrafları `photos/<kullanıcı-kimliği>/` altında fiziksel olarak
  ayrılır.
- Parolalar rastgele tuzlu scrypt özetiyle saklanır; açık parola kaydedilmez.
- Oturum anahtarlarının yalnızca SHA-256 özeti saklanır.
- Hatalı giriş denemeleri hesap ve bağlantı kaynağına göre kalıcı olarak sınırlandırılır.
- Yeni kullanıcı kaydı yalnızca süreli davet koduyla yapılır.
- Belge güncellemeleri sürüm denetimiyle istemciler arası veri ezilmesini önler.
- Android otomatik yedeklemesi bir güncel kopya ve son yedi güne ait dönen kopyalar tutar.
- Oturum, kullanıldıkça güvenli biçimde 30 gün uzatılır; 30 gün hiç kullanılmazsa sona erer.
  Parola cihazda saklanmaz.
- Yönetici, 30 gün bağlantı kurmayan aile üyelerinin bahçe erişimini gözden geçirir.
  “Kalsın” kararı takip süresini yeniden başlatır; yetki kaldırma hesabı ve kullanıcı
  verilerini silmeden yalnızca bahçe erişimini ve açık NAS oturumlarını kapatır.
- Aile hesabı yönetici tarafından devre dışı bırakıldığında bahçe erişimi ve tüm NAS
  oturumları hemen kapatılır; hesap verileri 30 gün geri alınabilir biçimde korunur.
- İlk 30 gün içinde hesap geri yüklenebilir. Süre dolduktan sonra kalıcı silme yalnızca
  yöneticinin güncel parolasıyla ve ayrı bir son onayla yapılır; otomatik veri silme yoktur.
- Yönetici hesabı bu yaşam döngüsü işlemlerinin hedefi olamaz.
- Fotoğraf kimlikleri ve yolları dizin geçişine karşı doğrulanır.

## NAS dizinleri

Portainer yığını aşağıdaki mevcut dizinleri kullanır:

```text
/share/Docker/AVORA
├── app
├── backups
├── config
├── database
├── logs
├── photos
└── tailscale
    └── config
        └── serve.json
```

Sunucu ilk kez başladığında `config/setup_token.txt` dosyasına tek kullanımlık kurulum
anahtarı yazar. İlk yönetici hesabı oluşturulunca bu dosya silinir. Daha sonraki açılışlarda
aktif hesap bulunduğu için kalıcı yeni bir kurulum anahtarı oluşturulmaz.

## Portainer kurulumu

1. `Dockerfile` ve `requirements.txt` bulunan dizinde
   `docker build -t avora-nas-api:firebase-7.5.0 .` ile API imajını hazırlayın.
   Dağıtım ZIP'ini `/share/Docker/AVORA` içine çıkarın. Arşiv `app` klasörünü ve
   `stack.yml` dosyasını oluşturur.
2. Portainer'da **Stacks → Add stack** açın ve adı `avora` yapın.
3. `stack.yml` içeriğini Web editor alanına yapıştırın ve yığını dağıtın.
4. Konteyner sağlıklı olduktan sonra NAS terminalinde
   `curl http://127.0.0.1:18787/health` komutuyla kontrol edin. Düz HTTP portu
   yalnız NAS loopback arayüzüne bağlıdır ve LAN cihazlarına açılmaz.
5. Tailscale Funnel HTTPS adresini doğruladıktan sonra yönetici hesabını bu güvenli
   adres üzerinden oluşturun. Kurulum anahtarını veya parolayı ekran görüntüsüyle
   paylaşmayın.

`18787` yalnızca `127.0.0.1` üzerinde dinler ve yönlendirici üzerinden internete
açılmamalıdır. CGNAT altındaki dış erişim,
`avora-tunnel` konteyneri ve Tailscale Funnel üzerinden sağlanır. Tailscale durumu
`tailscale/` dizininde kalıcı tutulur; yeniden başlatmada cihaz kimliği kaybolmaz.
`tailscale/config/serve.json` dosyası Funnel yönlendirmesini her konteyner açılışında
otomatik olarak yeniden uygular.

İlk dağıtımdan sonra Portainer'da `avora-tailscale` günlüklerindeki oturum açma bağlantısı
kullanılarak NAS Tailscale hesabına eklenir. Oturum açma tamamlanınca Funnel yapılandırması
`TS_SERVE_CONFIG` üzerinden otomatik yüklenir. Konteyner konsolunda `tailscale funnel status`
ile `Funnel on` ve `proxy http://127.0.0.1:8787` görüldüğü doğrulanır. Oluşan
`https://...ts.net` adresi AVORA istemcilerinin güvenli API adresidir. Test kullanıcılarının
cihazlarına Tailscale kurulması gerekmez. Funnel etkinleştirilmeden önce yönlendiricideki
`18788` kuralı devre dışı bırakılmalıdır.

## Başlıca API uçları

```text
GET  /health
POST /v1/setup
POST /v1/auth/login
POST /v1/auth/logout
POST /v1/auth/register
GET  /v1/me
POST /v1/account/password
POST /v1/account/sessions/revoke-others
POST /v1/account/session/device
GET  /v1/account/sessions
GET  /v1/admin/accounts?device_id={cihaz}
POST /v1/admin/inactive-access/keep
POST /v1/admin/device-access/revoke
POST /v1/admin/accounts/disable
POST /v1/admin/accounts/restore
POST /v1/admin/accounts/delete
POST /v1/admin/invites
POST /v1/admin/invites/revoke
GET  /v1/data/documents
GET  /v1/data/documents/{key}
PUT  /v1/data/documents/{key}
GET  /v1/photos
GET  /v1/photos/{id}
PUT  /v1/photos/{id}
PATCH /v1/photos/{id}
POST /v1/photos/{id}/metadata
```

Korunan uçlar `Authorization: Bearer <oturum-anahtarı>` ister. Parola değişikliği
mevcut parolayı tekrar doğrular, bu telefondaki geçerli oturumu korur ve aynı hesaba ait
diğer oturumları kapatır. Ayrı oturum kapatma ucu da bu telefondaki oturumu koruyarak
yalnızca diğer cihazları çıkarır.

Hesap listesi yalnızca yöneticiye açıktır; ad, e-posta, rol, hesap tarihi, son NAS
bağlantısı, ilgili cihazın bahçe erişim durumu ve aktif oturum sayısını döndürür. Yönetici
hesabı pasif erişim incelemesine hiçbir zaman dahil edilmez. Parola özeti ve oturum
anahtarları bu yanıta dahil edilmez.

Her hesap yalnızca kendi açık oturumlarının cihaz adı, oluşturulma, son görülme ve sona
erme zamanlarını görebilir. Cihaz kimliği uygulama kurulumunda rastgele üretilir; donanım
seri numarası kullanılmaz. Oturum anahtarı hiçbir liste yanıtında gösterilmez.

Yeni hesaplar yalnızca yöneticinin oluşturduğu süreli ve kullanımı sınırlı davet koduyla
açılır. Davet kodunun yalnızca özeti saklanır; kullanılmamış bir kod yönetici tarafından
iptal edilebilir. Her yeni kullanıcı ayrı veri tabanı ve fotoğraf dizini alır.

Fotoğraf yükleme isteğinin içerik türü `image/jpeg` olmalıdır. Sunucuda CORS açılmaz.

Android uygulaması fotoğrafları kimlik ve SHA-256 özetiyle kademeli gönderir. Otomatik
fotoğraf yedeklemesi yalnızca ölçülmeyen ağda ve pil düşük değilken çalışır. Geri yükleme
sadece telefonda eksik olan, boyutu ve özeti doğrulanmış JPEG dosyalarını ekler; mevcut
telefon dosyalarını değiştirmez ve NAS arşivinden otomatik silme yapmaz.

## E-posta ile şifre kurtarma

Android NAS giriş pencerelerindeki **Şifremi unuttum** ekranı önce hesap e-postasını,
ardından e-postadaki kodu ve yeni şifreyi ister. Şifre 12–128 karakter olmalıdır.
Hesabın kayıtlı olup olmadığı API yanıtından anlaşılmaz. Kod 15 dakika geçerlidir;
sıfırlama bütün NAS oturumlarını ve hesabın diğer kurtarma kodlarını iptal eder.
Günlükler, fotoğraflar ve cihaz erişim onayları korunur. Normal parola değişikliği ve
hesabın devre dışı bırakılması da bekleyen kurtarma kodlarını iptal eder.

Portainer stack ortam değişkenlerinde aşağıdaki ayarları tanımlayın. `stack.yml`
değerleri bu değişkenlerden alır; gerçek SMTP parolasını repoya yazmayın.

| Değişken | Açıklama |
| --- | --- |
| `AVORA_SMTP_HOST` | SMTP sunucusu; boşken kurtarma kapalıdır |
| `AVORA_SMTP_PORT` | STARTTLS için genellikle `587`; SSL için genellikle `465` |
| `AVORA_SMTP_SECURITY` | `starttls` (varsayılan) veya `ssl`; şifresiz aktarım desteklenmez |
| `AVORA_SMTP_FROM` | Sağlayıcının izin verdiği gönderen e-posta adresi |
| `AVORA_SMTP_USERNAME` | SMTP kullanıcı adı |
| `AVORA_SMTP_PASSWORD` | SMTP parolası veya sağlayıcının uygulama parolası |

Kullanıcı adı ve parola birlikte tanımlanmalıdır. Sunucu sertifikası doğrulanır.
SMTP kurulumundan sonra ayrı bir dağıtım adımında NAS API kaynakları ve stack
güncellenmelidir. Yalnızca Android APK güncellemesi canlı sunucuda kurtarmayı açmaz.
Bu geliştirme sırasında çalışan NAS/Portainer ayarları değiştirilmemiştir.

Yeni, oturum gerektirmeyen uçlar:

- `POST /v1/auth/forgot-password`: `{"email":"hesap@example.com"}` → `202 {"accepted":true}`.
- `POST /v1/auth/reset-password`: `email`, `code`, `new_password` → `200 {"reset":true}`.

SMTP kapalıysa istek `503 recovery_unavailable`, geçersiz/kullanılmış/süresi dolmuş
kod `400 invalid_reset_token` döner. Şifre politikası `400 weak_password`; deneme
sınırı `429 rate_limited` ve `Retry-After` başlığı ile bildirilir. İlk e-posta isteğinde
hesap başına 15 dakikada 3, kaynak başına 30 istek; doğrulamada hesap başına 10,
kaynak başına 30 deneme sınırı uygulanır. Sınırlar yeniden başlatmalarda korunur
ve normal giriş denemelerinden ayrıdır. API tek süreç olarak çalıştırılmalıdır.

E-postalar en fazla 32 işlik bellek kuyruğunda, tek arka plan işçisiyle gönderilir;
`202` teslim garantisi değildir. Yeniden başlatmada bekleyen işler kaybolabilir;
kullanıcı yeni kod isteyebilir. SMTP hatası alan kod iptal edilir. Teslim hataları
`Password recovery delivery failed; check SMTP configuration.` olarak kaydedilir;
adres, kod, parola ve SMTP hata ayrıntısı loglanmaz. Tekrarlayan hatalarda SMTP
ayarları ve sağlayıcının gönderen adresi izni kontrol edilmelidir.

Yerel doğrulama: `python -m unittest discover -s tests -v`. Kurtarma testleri SMTP'yi
taklit eder; gerçek e-posta göndermez. Canlı teslimat ayrıca yapılandırma sonrası
kontrollü bir hesapla doğrulanmalıdır.

## Yeniden kurulum sonrası kalıcı yönetici erişimi

NAS'a giriş ile Firebase bahçe erişimi ayrı doğrulamalardır. Uygulama yeniden
kurulduğunda anonim UID değiştiği için eski telefona verilmiş yetki taşınmaz.
`POST /v1/auth/firebase-owner-session`, geçerli NAS **yönetici** oturumunu doğrular
ve hesap UUID'sinden türetilen sabit `avora_nas_<uuid>` Firebase kimliği için özel
oturum anahtarı üretir. Android bu anahtarla giriş yapar, güncel
`avora_device_id` yetkisini kontrol eder ve sonra bahçe verilerini açar.
Telefon kimliği, Firebase UID'si, rol veya hedef cihaz istemciden alınmaz.
Aile üyelerine bu uç 403 döner; mevcut cihaz onay akışı korunur.

- `AVORA_FIREBASE_DEVICE_ID=avora-001`: Bu NAS yöneticisinin yetkili olduğu bahçe.
- `AVORA_FIREBASE_CREDENTIALS_FILE=/data/config/firebase-service-account.json`:
  Aynı Firebase projesine ait hizmet hesabı; dosya yalnız NAS config dizininde tutulur,
  repoya/APK'ya eklenmez ve API üzerinden gönderilmez. Dosya izinlerini 600 yapın.
- Çalışan imaj Firebase Admin SDK içermelidir; `NasServer/Dockerfile` bunu kurar.

Bağlı NAS yönetici oturumu varsa uygulama açılışında ve NAS güvenlik ekranında
eksik Firebase kimliği otomatik onarılır. NAS hesabından çıkış ve aile hesabına
geçiş, NAS kaynaklı yönetici Firebase oturumunu telefonda kapatır. Hesaplar aynı
bahçe için aynı kayıtları görür; veriler taşınmaz, silinmez ve Firebase kuralları
gevşetilmez. Mevcut başka özel yetkiler korunur; farklı bahçeye bağlı veya devre
dışı bırakılmış Firebase kimliği otomatik değiştirilmez.

Mevcut NAS için `deploy_owner_update.py`, kaynakları, yapılandırmayı ve SQLite'ın
tutarlı kopyasını yedekler. Hazırlanan imajı önce ayrı bir konteynerde, hesap
veritabanının kopyasıyla sınar; bu konteyner dışarıya port açmaz. İki bağımsız
girişin aynı yönetici kimliğini kullanması ve mevcut bahçe verilerinin okunması
doğrulanınca canlı API ve tünel yenilenir. Canlı doğrulama başarısız olursa eski
kaynak ve konteyner yapılandırması geri alınır; kullanıcı veritabanı geri
yüklenmez veya silinmez. Mevcut port bağlamaları aynen korunur; bu işlem eski
bir kurulumun LAN'a açık portunu kendiliğinden loopback'e taşımaz.

Doğrulama hataları yalnız aşama, hata sınıfı ve varsa HTTP durumunu raporlar.
Firebase `signInWithCustomToken` yanıtındaki `idToken` içinden `sub` alanı
okunur; yanıtta `localId` bulunduğu varsayılmaz. Yetki, aynı token ile gerçek
Firebase veri okuması yapılarak da doğrulanır.

Dağıtım raporundaki `portainer_source_updated` false ise Portainer'ın sakladığı
yığın ayrıca eşitlenmelidir; aksi halde Portainer üzerinden sonraki yeniden
dağıtım eski yapılandırmayı kullanabilir. Bu NAS için `sync_owner_portainer.py`,
başarılı doğrulama raporunu ve her iki dosyanın AVORA servis adlarını kontrol
eder, eski Portainer kaydını aynı yedek dizinine alır ve yeni tanımı atomik
olarak kaydeder. Konteynerleri yeniden başlatmaz.
