# AVORA ESP32

Bu klasör, kablosuz toprak nem sensörlerini MQTT ile AVORA sistemine
gönderen ESP32/Arduino kaynak kodunu içerir.

Ana Arduino projesini bu klasöre, kendi klasörüyle birlikte ekleyin:

```text
Arduino/
  ESP32/
    AVORA-Sensors/
      esp32/
        esp32.ino
        secrets.h
```

Hedef donanım yapısı:

- ADS1115 `0x48`: `soil-001` ile `soil-004`
- ADS1115 `0x49`: `soil-005` ile `soil-008`
- MQTT konuları: `avora/sensors/soil-001` ... `soil-008`

İkinci ADS1115 henüz bağlı değilken ilk dört sensör çalışmaya devam etmeli;
kalan dört kanal bağlantı bekliyor olarak ele alınmalıdır.

Firmware her iki ADS1115 adresini bağımsız denetler. `0x48` veya `0x49`
modüllerinden biri sökülür ya da yanıt vermezse yalnız o modüle bağlı dört kanal
durur; diğer ADS1115 veri göndermeye devam eder. Modül durumları kalıcı olarak
`avora/status/esp32/ads1115` MQTT konusuna yayımlanır ve bağlantı 30 saniyede
bir yeniden denenir. Değiştirilen modül güç güvenli biçimde yeniden takıldığında
uygulama ayarı gerekmeden otomatik devreye alınır.

## MQTT sunucusunu otomatik bulma

ESP32, Raspberry Pi'nin `_mqtt._tcp` mDNS ilanını tarar ve yalnızca
`device=avora-001` TXT kaydını taşıyan hizmete bağlanır. Böylece Raspberry Pi
statik veya dinamik IP kullansa da sensörlerin MQTT hedefi otomatik güncellenir.

Raspberry tarafında ilanı kurmak için:

```bash
cd /home/ali/AVORA/RaspberryPi
sudo ./deploy/install-mqtt-discovery.sh
```

mDNS ilanı bulunamazsa firmware geçici olarak `192.168.1.99:1883` adresini
geri dönüş hedefi olarak dener ve her 10 saniyede keşfi tekrarlar.

## Sensörü uzaktan açma / kapatma

ESP32, aşağıdaki MQTT konusundaki kalıcı (retained) ayarları dinler:

```text
avora/config/esp32/sensors/<sensor_id>
```

Örnekler:

```text
avora/config/esp32/sensors/soil-003  ->  1  (aç)
avora/config/esp32/sensors/soil-003  ->  0  (kapat)
```

Bu bağlantıyı Android uygulaması Firebase üzerinden Raspberry Pi'ye
gönderecek; ESP32 yeniden başlatılsa bile retained ayar tekrar uygulanır.

## Kalibrasyonu uzaktan güncelleme

Kullanıcı Android uygulamasında sensörün kuru ve ıslak ham değerlerini
girdiğinde ESP32 aşağıdaki kalıcı ayarı alır:

```text
avora/config/esp32/calibration/<sensor_id>
```

Mesaj biçimi `kuru,ıslak` şeklindedir:

```text
avora/config/esp32/calibration/soil-003  ->  12480,620
```

ESP32 yalnızca kuru değer ıslak değerden büyük olduğunda kalibrasyonu kabul
eder. Böylece hatalı girişler sulama kararını etkileyemez.
