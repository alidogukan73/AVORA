# Firebase cihaz sahipliği güvenli geçişi

Bu geçiş, anonim olarak oturum açan her Android istemcisinin bütün AVORA
cihazlarına erişebilmesi riskini kaldırır. Android kullanıcısına Raspberry Pi
hizmet hesabıyla `avora_device_id=avora-001` özel yetkisi verilir.

## Önemli sıra

Yeni güvenlik kurallarını sahiplik yetkisi verilmeden yayımlamayın. Aksi halde
Android uygulaması Firebase erişimini kaybeder. Güvenli sıra şöyledir:

1. Son geri bildirim e-postasındaki `Kullanıcı kimliği` değerini alın.
2. Raspberry Pi üzerinde kullanıcıyı cihaza bağlayın.
3. Android uygulamasının bu değişiklikleri içeren sürümünü kurun.
4. Android uygulamasını tamamen kapatıp yeniden açın.
5. Uygulamanın bölgeleri okuyabildiğini ve bir ayarı kaydedebildiğini doğrulayın.
6. Son olarak yeni Realtime Database kurallarını yayımlayın.

## Raspberry Pi üzerinde sahiplik verme

```bash
cd ~/AVORA
git pull --ff-only origin main
cd RaspberryPi
.venv/bin/python tools/configure_device_owner.py --uid 'FIREBASE_KULLANICI_KIMLIGI'
```

Araç, mevcut farklı özel yetkileri korur. Kullanıcı zaten başka bir AVORA
cihazına bağlıysa yanlışlıkla üzerine yazmaz.

Mevcut yetkili kullanıcıları değiştirmeden listelemek için:

```bash
cd ~/AVORA/RaspberryPi
.venv/bin/python tools/configure_device_owner.py --list
```

## Kuralları önce emülatörde sınama

Proje kökünde:

```powershell
cd D:\Projects\AVORA\firebase-rules-tests
npm.cmd install
npm.cmd test
```

Testler gerçek `avora-alidogukan` veritabanına bağlanmaz; yalnız
`demo-avora-alidogukan` emülatörünü kullanır.

## Kuralları yayımlama

Android sahipliği doğrulandıktan sonra proje kökünde:

```powershell
cd D:\Projects\AVORA
npx.cmd --yes firebase-tools@15.28.2 deploy --only database --project avora-alidogukan
```

Bu işlem Cloud Functions veya Blaze planı gerektirmez.

## Telefon değişikliği veya uygulama verilerinin silinmesi

NAS yöneticisi için kalıcı yol artık NAS hesabıyla oturum açmaktır. NAS API'nin
`firebase-owner-session` desteği, Firebase Admin SDK ve hizmet hesabı yapılandırıldıysa
Android aynı NAS hesabını her kurulumda aynı `avora_nas_<hesap UUID>` Firebase
kimliğine bağlar. `avora_device_id` sunucuda atanır ve telefonda doğrulanır.
Mevcut NAS yönetici oturumu, uygulama açılışında eksik Firebase oturumunu otomatik
onarır. Aile hesapları yönetici kimliği alamaz; mevcut onay akışını kullanır.
Kurulum ayrıntıları için `NasServer/README.md` içindeki kalıcı yönetici erişimi
bölümüne bakın. Bu yol Firebase kurallarında gevşetme gerektirmez.

Eski manuel yetkilendirilmiş kurulumlar için aşağıdaki araçlar geçerlidir:

Anonim Firebase kullanıcı kimliği uygulama verileri silindiğinde değişebilir.
Yeni kimliğe sahiplik verdikten sonra eski kimliğin erişimini kaldırın:

```bash
cd ~/AVORA/RaspberryPi
.venv/bin/python tools/configure_device_owner.py \
  --uid 'ESKI_FIREBASE_KULLANICI_KIMLIGI' \
  --remove
```

Yeni telefonun kimliği henüz e-postada görünmüyorsa güvenlik kurallarını geçici
olarak gevşetmeyin. Firebase Authentication kullanıcı listesinden yeni anonim
kimliği belirleyin ve Raspberry Pi aracıyla yetkilendirin.
