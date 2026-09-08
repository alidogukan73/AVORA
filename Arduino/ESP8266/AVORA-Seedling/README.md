# AVORA Fide Sensör Düğümü

Bu yazılım fotoğraftaki **NodeMCU ESP8266** kartı için hazırlanmıştır. Fide
ortamını izler; röle, pompa veya ısıtıcı çalıştırmaz.

## Bağlantılar

Tüm modülleri 3.3 V ile besleyin ve GND hatlarını ortaklayın.

| Modül | NodeMCU bağlantısı |
|---|---|
| SHT3x | SDA → D2 (GPIO4), SCL → D1 (GPIO5) |
| BH1750 | SDA → D2 (GPIO4), SCL → D1 (GPIO5) |
| ADS1115 | SDA → D2 (GPIO4), SCL → D1 (GPIO5) |
| DS18B20 | DATA → D5 (GPIO14), VCC → 3.3 V, GND → GND |
| Kapasitif nem | AO → ADS1115 A0, VCC → 3.3 V, GND → GND |

DS18B20 DATA ile 3.3 V arasına **4.7 kΩ** pull-up direnç takılmalıdır.
ESP8266 girişlerine 5 V uygulanmamalıdır.

Beklenen I2C adresleri SHT3x `0x44`, BH1750 `0x23`, ADS1115 `0x48`.

## Arduino kütüphaneleri

Library Manager: PubSubClient, Adafruit ADS1X15, Adafruit SHT31 Library,
BH1750, OneWire ve DallasTemperature. Kart yöneticisinden `esp8266 by
ESP8266 Community` paketini kurup `NodeMCU 1.0 (ESP-12E Module)` seçin.

## Kurulum

1. `secrets.example.h` dosyasını `secrets.h` adıyla kopyalayın.
2. Wi-Fi adını ve parolasını girin.
3. Raspberry Pi IP adresini yedek adres olarak doğrulayın.
4. Kodu yükleyip Seri Monitörü 115200 baud ile açın.

Kart öncelikle Raspberry Pi üzerindeki `_mqtt._tcp` mDNS servisini arar. Bu
sayede Raspberry Pi IP adresi değişse bile MQTT sunucusunu yeniden bulabilir.
Keşif başarısız olursa `MQTT_FALLBACK_HOST` kullanılır.

## Toprak nem kalibrasyonu

`SOIL_DRY_RAW` ve `SOIL_WET_RAW` başlangıç değeridir. Sensörün kuru ve tamamen
nemli yetiştirme harcındaki ham değerlerini ölçerek bu sabitleri güncelleyin.
Elektronik kısmı ıslatmayın; yalnız prob yüzeyi harca girmelidir.

Telemetri: `avora/seedling/seedling-001/telemetry`

ADS1115 takılı değilse diğer sensörler veri göndermeye devam eder ve toprak
nemi `soil_moisture_available=false` olarak işaretlenir. Firmware I2C hattını
30 saniyede bir yeniden tarar; ADS1115 sonradan takıldığında ayar veya yeniden
yükleme gerekmeden otomatik olarak devreye girer.
