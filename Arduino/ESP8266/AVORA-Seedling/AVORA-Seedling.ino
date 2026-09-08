#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <ESP8266mDNS.h>
#include <PubSubClient.h>
#include <Wire.h>
#include <Adafruit_ADS1X15.h>
#include <Adafruit_SHT31.h>
#include <BH1750.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include "secrets.h"

namespace {
constexpr char NODE_ID[] = "seedling-001";
constexpr char AVORA_DEVICE_ID[] = "avora-001";
constexpr char FIRMWARE_VERSION[] = "1.3.0";
constexpr char MQTT_TOPIC[] = "avora/seedling/seedling-001/telemetry";
constexpr char MQTT_STATUS_TOPIC[] = "avora/seedling/seedling-001/status";
constexpr uint8_t I2C_SDA_PIN = D2;
constexpr uint8_t I2C_SCL_PIN = D1;
constexpr uint8_t DS18B20_PIN = D5;
constexpr uint32_t PUBLISH_INTERVAL_MS = 10000;
constexpr uint32_t ADS1115_RETRY_INTERVAL_MS = 30000;
constexpr int16_t SOIL_DRY_RAW = 11244;
constexpr int16_t SOIL_WET_RAW = 2224;

WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);
Adafruit_SHT31 sht31;
Adafruit_ADS1115 ads1115;
BH1750 lightMeter;
OneWire oneWire(DS18B20_PIN);
DallasTemperature rootThermometer(&oneWire);
bool hasSht31 = false, hasAds1115 = false, hasBh1750 = false;
uint8_t ads1115Address = 0;
unsigned long lastPublishAt = 0;
unsigned long lastAds1115DiscoveryAt = 0;
String brokerHost = MQTT_FALLBACK_HOST;
uint16_t brokerPort = MQTT_FALLBACK_PORT;
bool mdnsStarted = false;
bool brokerDiscovered = false;

int moisturePercent(int16_t raw) {
  long mapped = map(raw, SOIL_DRY_RAW, SOIL_WET_RAW, 0, 100);
  return constrain(mapped, 0, 100);
}

bool beginAds1115() {
  constexpr uint8_t addresses[] = {0x48, 0x49, 0x4A, 0x4B};
  for (uint8_t address : addresses) {
    if (ads1115.begin(address)) {
      ads1115Address = address;
      Serial.printf("ADS1115 bulundu: 0x%02X\n", address);
      return true;
    }
  }
  Serial.println("ADS1115 bulunamadi (0x48-0x4B). Baglantiyi kontrol edin.");
  return false;
}

bool ads1115Connected() {
  if (!hasAds1115 || ads1115Address == 0) return false;
  Wire.beginTransmission(ads1115Address);
  return Wire.endTransmission() == 0;
}

void refreshAds1115() {
  if (ads1115Connected()) return;
  hasAds1115 = false;
  ads1115Address = 0;
  const unsigned long now = millis();
  if (lastAds1115DiscoveryAt != 0
      && now - lastAds1115DiscoveryAt < ADS1115_RETRY_INTERVAL_MS) {
    return;
  }
  lastAds1115DiscoveryAt = now;
  hasAds1115 = beginAds1115();
  if (hasAds1115) {
    Serial.println("ADS1115 otomatik olarak devreye alindi.");
  }
}

void connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    yield();
  }
  Serial.printf("Wi-Fi baglandi: %s, IP=%s\n",
                WIFI_SSID, WiFi.localIP().toString().c_str());
}

void onMqttServiceChanged(MDNSResponder::MDNSServiceInfo serviceInfo,
                          MDNSResponder::AnswerType,
                          bool setContent) {
  if (!setContent || brokerDiscovered || !serviceInfo.hostPortAvailable()
      || !serviceInfo.IP4AddressAvailable() || !serviceInfo.txtAvailable()) {
    return;
  }
  const char* device = serviceInfo.value("device");
  if (device == nullptr || strcmp(device, AVORA_DEVICE_ID) != 0) return;
  std::vector<IPAddress> addresses = serviceInfo.IP4Adresses();
  if (addresses.empty() || serviceInfo.hostPort() == 0) return;
  brokerHost = addresses.front().toString();
  brokerPort = serviceInfo.hostPort();
  brokerDiscovered = true;
}

bool discoverBroker() {
  if (!mdnsStarted) {
    if (!MDNS.begin(NODE_ID)) return false;
    mdnsStarted = true;
  }
  brokerDiscovered = false;
  MDNSResponder::hMDNSServiceQuery query =
      MDNS.installServiceQuery("mqtt", "tcp", onMqttServiceChanged);
  if (query == nullptr) return false;
  const unsigned long startedAt = millis();
  while (!brokerDiscovered && millis() - startedAt < 2500) {
    MDNS.update();
    delay(10);
    yield();
  }
  MDNS.removeServiceQuery(query);
  if (brokerDiscovered) {
    Serial.printf("MQTT mDNS ile bulundu: %s:%u\n", brokerHost.c_str(), brokerPort);
    return true;
  }
  Serial.println("AVORA MQTT mDNS bulunamadi; yedek adres kullaniliyor.");
  return false;
}

void connectMqtt() {
  while (!mqtt.connected()) {
    String clientId = String("avora-") + NODE_ID + "-" + ESP.getChipId();
    if (mqtt.connect(clientId.c_str(), MQTT_STATUS_TOPIC, 0, true, "offline")) {
      mqtt.publish(MQTT_STATUS_TOPIC, "online", true);
      Serial.printf("MQTT baglandi: %s:%u\n", brokerHost.c_str(), brokerPort);
      return;
    }
    Serial.printf("MQTT baglanti hatasi: %d\n", mqtt.state());
    delay(2000);
    yield();
    if (WiFi.status() != WL_CONNECTED) connectWifi();
    if (discoverBroker()) mqtt.setServer(brokerHost.c_str(), brokerPort);
  }
}

void publishTelemetry() {
  float airTemperature = hasSht31 ? sht31.readTemperature() : NAN;
  float airHumidity = hasSht31 ? sht31.readHumidity() : NAN;
  rootThermometer.requestTemperatures();
  float rootTemperature = rootThermometer.getTempCByIndex(0);
  if (rootTemperature == DEVICE_DISCONNECTED_C) rootTemperature = NAN;
  refreshAds1115();
  const bool soilMoistureAvailable = hasAds1115;
  int16_t soilRaw = soilMoistureAvailable ? ads1115.readADC_SingleEnded(0) : 0;
  float lux = hasBh1750 ? lightMeter.readLightLevel() : NAN;
  if (!isfinite(airTemperature) || !isfinite(airHumidity)
      || !isfinite(rootTemperature) || !isfinite(lux)) {
    Serial.println("Eksik veya gecersiz sensor olcumu; paket gonderilmedi.");
    return;
  }
  char payload[560];
  snprintf(payload, sizeof(payload),
      "{\"node_id\":\"%s\",\"firmware\":\"%s\"," 
      "\"air_temperature_c\":%.2f,\"air_humidity_pct\":%.2f,"
      "\"root_temperature_c\":%.2f,\"soil_moisture_available\":%s,"
      "\"soil_moisture_pct\":%d,"
      "\"soil_raw\":%d,\"light_lux\":%.2f,\"rssi\":%d,"
      "\"uptime_seconds\":%lu}", NODE_ID, FIRMWARE_VERSION,
      airTemperature, airHumidity, rootTemperature,
      soilMoistureAvailable ? "true" : "false",
      soilMoistureAvailable ? moisturePercent(soilRaw) : 0,
      soilRaw, lux, WiFi.RSSI(), millis() / 1000UL);
  if (mqtt.publish(MQTT_TOPIC, payload, false)) {
    Serial.println(payload);
  }
}
}  // namespace

void setup() {
  Serial.begin(115200);
  delay(300);
  Wire.begin(I2C_SDA_PIN, I2C_SCL_PIN);
  hasSht31 = sht31.begin(0x44);
  hasAds1115 = beginAds1115();
  lastAds1115DiscoveryAt = millis();
  hasBh1750 = lightMeter.begin(BH1750::CONTINUOUS_HIGH_RES_MODE, 0x23);
  rootThermometer.begin();
  Serial.printf("SHT3x=%s, BH1750=%s, DS18B20=%s\n",
                hasSht31 ? "OK" : "YOK",
                hasBh1750 ? "OK" : "YOK",
                rootThermometer.getDeviceCount() > 0 ? "OK" : "YOK");
  connectWifi();
  discoverBroker();
  mqtt.setServer(brokerHost.c_str(), brokerPort);
  mqtt.setBufferSize(640);
  connectMqtt();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) connectWifi();
  if (!mqtt.connected()) connectMqtt();
  mqtt.loop();
  MDNS.update();
  unsigned long now = millis();
  if (now - lastPublishAt >= PUBLISH_INTERVAL_MS) {
    lastPublishAt = now;
    publishTelemetry();
  }
  delay(10);
}
