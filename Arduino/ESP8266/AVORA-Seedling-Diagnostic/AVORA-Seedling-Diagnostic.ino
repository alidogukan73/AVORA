#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_ADS1X15.h>
#include <Adafruit_SHT31.h>
#include <BH1750.h>
#include <OneWire.h>
#include <DallasTemperature.h>

namespace {
constexpr uint8_t I2C_SDA_PIN = D2;
constexpr uint8_t I2C_SCL_PIN = D1;
constexpr uint8_t DS18B20_PIN = D5;
constexpr uint32_t SAMPLE_INTERVAL_MS = 2000;

Adafruit_SHT31 sht31;
Adafruit_ADS1115 ads1115;
BH1750 lightMeter;
OneWire oneWire(DS18B20_PIN);
DallasTemperature rootThermometer(&oneWire);

bool hasSht31 = false;
bool hasAds1115 = false;
bool hasBh1750 = false;
bool hasDs18b20 = false;
uint8_t ads1115Address = 0;
unsigned long lastSampleAt = 0;

void scanI2cBus() {
  Serial.println(F("\nI2C taramasi basliyor..."));
  uint8_t found = 0;
  for (uint8_t address = 1; address < 127; ++address) {
    Wire.beginTransmission(address);
    if (Wire.endTransmission() == 0) {
      Serial.printf("  Bulundu: 0x%02X\n", address);
      ++found;
    }
  }
  Serial.printf("I2C cihaz sayisi: %u (beklenen: 3)\n", found);
}

void printStatus(const __FlashStringHelper* label, bool ok) {
  Serial.print(label);
  Serial.println(ok ? F(" OK") : F(" YOK/HATA"));
}

bool beginAds1115() {
  constexpr uint8_t addresses[] = {0x48, 0x49, 0x4A, 0x4B};
  for (uint8_t address : addresses) {
    if (ads1115.begin(address)) {
      ads1115Address = address;
      return true;
    }
  }
  return false;
}

void readSensors() {
  Serial.println(F("\n--- Sensor olcumleri ---"));

  if (hasSht31) {
    const float temperature = sht31.readTemperature();
    const float humidity = sht31.readHumidity();
    Serial.printf("SHT3x hava: %.2f C, %.2f %%RH\n", temperature, humidity);
  } else {
    Serial.println(F("SHT3x: veri yok"));
  }

  if (hasBh1750) {
    Serial.printf("BH1750 isik: %.2f lux\n", lightMeter.readLightLevel());
  } else {
    Serial.println(F("BH1750: veri yok"));
  }

  if (hasAds1115) {
    Serial.printf("ADS1115 0x%02X A0 / toprak ham deger: %d\n",
                  ads1115Address, ads1115.readADC_SingleEnded(0));
  } else {
    Serial.println(F("ADS1115: veri yok"));
  }

  if (hasDs18b20) {
    rootThermometer.requestTemperatures();
    const float temperature = rootThermometer.getTempCByIndex(0);
    if (temperature == DEVICE_DISCONNECTED_C) {
      Serial.println(F("DS18B20: okuma hatasi (4.7k direnci kontrol et)"));
    } else {
      Serial.printf("DS18B20 kok sicakligi: %.2f C\n", temperature);
    }
  } else {
    Serial.println(F("DS18B20: bulunamadi (DATA/D5 ve 4.7k direnci kontrol et)"));
  }
}
}  // namespace

void setup() {
  Serial.begin(115200);
  delay(800);
  Serial.println(F("\nAVORA fide sensor donanim testi"));
  Serial.println(F("NodeMCU: SDA=D2, SCL=D1, DS18B20=D5, besleme=3.3V"));

  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  scanI2cBus();

  hasSht31 = sht31.begin(0x44);
  hasAds1115 = beginAds1115();
  hasBh1750 = lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE, 0x23);
  rootThermometer.begin();
  hasDs18b20 = rootThermometer.getDeviceCount() > 0;

  Serial.println(F("\n--- Baglanti sonucu ---"));
  printStatus(F("SHT3x (0x44):"), hasSht31);
  printStatus(F("BH1750 (0x23):"), hasBh1750);
  if (hasAds1115) {
    Serial.printf("ADS1115 (0x%02X): OK\n", ads1115Address);
  } else {
    Serial.println(F("ADS1115 (0x48-0x4B): YOK/HATA"));
  }
  printStatus(F("DS18B20 (D5):"), hasDs18b20);
  readSensors();
}

void loop() {
  const unsigned long now = millis();
  if (now - lastSampleAt >= SAMPLE_INTERVAL_MS) {
    lastSampleAt = now;
    readSensors();
  }
  delay(10);
}
