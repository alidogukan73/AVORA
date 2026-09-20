# AVORA NAS Sunucusu

Bu paket, Firebase'in yanında bağımsız olarak çalışacak hafif AVORA sunucu temelidir.
Android uygulamasındaki Veri Eşitleme ekranı NAS servisinin durumunu denetleyebilir ve
isteğe bağlı NAS hesabı açabilir. Bahçe verileri ve Raspberry Pi akışı şimdilik Firebase'i
kullanmaya devam eder; mevcut çalışma düzeni değişmez.

## Tasarım

- Yalnızca Python standart kütüphanesi kullanılır.
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

1. Dağıtım ZIP'ini `/share/Docker/AVORA` içine çıkarın. Arşiv `app` klasörünü ve
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
