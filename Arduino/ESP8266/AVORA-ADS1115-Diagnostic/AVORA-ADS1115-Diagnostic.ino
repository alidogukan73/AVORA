#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_ADS1X15.h>

namespace {
constexpr uint8_t I2C_SDA_PIN = D2;
constexpr uint8_t I2C_SCL_PIN = D1;
constexpr uint32_t SAMPLE_INTERVAL_MS = 1000;

Adafruit_ADS1115 ads1115;
bool adsReady = false;
uint8_t adsAddress = 0;
unsigned long lastSampleAt = 0;

bool beginAds1115() {
  constexpr uint8_t addresses[] = {0x48, 0x49, 0x4A, 0x4B};
  for (uint8_t address : addresses) {
    if (ads1115.begin(address)) {
      adsAddress = address;
      return true;
    }
  }
  return false;
}
}  // namespace

void setup() {
  Serial.begin(115200);
  delay(800);
  Serial.println(F("AVORA ADS1115 A0-3V3 testi"));
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  adsReady = beginAds1115();
  if (adsReady) {
    ads1115.setGain(GAIN_TWOTHIRDS);
    Serial.printf("ADS1115 bulundu: 0x%02X\n", adsAddress);
  } else {
    Serial.println(F("ADS1115 bulunamadi: 0x48-0x4B"));
  }
}

void loop() {
  const unsigned long now = millis();
  if (now - lastSampleAt < SAMPLE_INTERVAL_MS) return;
  lastSampleAt = now;

  if (!adsReady) {
    adsReady = beginAds1115();
    if (!adsReady) {
      Serial.println(F("ADS1115 bulunamadi"));
      return;
    }
    ads1115.setGain(GAIN_TWOTHIRDS);
    Serial.printf("ADS1115 sonradan bulundu: 0x%02X\n", adsAddress);
  }

  const int16_t raw = ads1115.readADC_SingleEnded(0);
  const float volts = ads1115.computeVolts(raw);
  Serial.printf("ADS1115 0x%02X A0: raw=%d voltage=%.4f V\n",
                adsAddress, raw, volts);
}
