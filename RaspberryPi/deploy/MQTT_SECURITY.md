# AVORA MQTT güvenli geçişi

Bu işlem sensör bağlantısını kesmemek için iki aşamalıdır. Parolaları repoya,
ekran görüntüsüne veya sohbet mesajına koymayın.

## 1. Kimlik bilgilerini hazırlayın

Raspberry Pi üzerinde iki ayrı kullanıcı oluşturun. Komutlar parolayı etkileşimli
olarak ister ve terminal geçmişine yazmaz:

```bash
sudo mosquitto_passwd -c /etc/mosquitto/passwd avora-pi
sudo mosquitto_passwd /etc/mosquitto/passwd avora-sensors
sudo chown root:mosquitto /etc/mosquitto/passwd
sudo chmod 640 /etc/mosquitto/passwd
```

`/etc/avora/mqtt.env` dosyasını yalnız root okuyabilmelidir:

```text
AVORA_MQTT_USERNAME=avora-pi
AVORA_MQTT_PASSWORD=PI_ICIN_OLUSTURULAN_PAROLA
```

```bash
sudo chown root:root /etc/avora/mqtt.env
sudo chmod 600 /etc/avora/mqtt.env
```

## 2. İstemcileri hazırlayın

- ESP32 ve ESP8266 `secrets.h` dosyalarına `avora-sensors` kullanıcısını ve
  ona ait parolayı ekleyip iki cihazı da yeniden yükleyin.
- Raspberry Pi'ye güncel `avora.service` dosyasını kurup `daemon-reload` yapın.
- Bu aşamada eski broker anonim bağlantıyı kabul ettiği için sensörler çalışmaya
  devam eder.

## 3. Broker zorunluluğunu en son açın

```bash
sudo install -m 640 -o root -g mosquitto RaspberryPi/deploy/mosquitto/avora.acl /etc/mosquitto/avora.acl
sudo install -m 644 RaspberryPi/deploy/mosquitto/avora.conf /etc/mosquitto/conf.d/avora.conf
sudo mosquitto -c /etc/mosquitto/mosquitto.conf -t
sudo systemctl restart mosquitto.service avora.service
```

Doğrulama:

```bash
sudo journalctl -u mosquitto.service -u avora.service -n 100 --no-pager
```

Loglarda üç Raspberry Pi MQTT istemcisinin kimlik doğrulayarak bağlandığı ve
ESP32/ESP8266 telemetrisinin güncel kaldığı görülmeden geçiş tamamlanmış sayılmaz.

