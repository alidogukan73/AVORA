//
// AVORA Wireless Soil Sensors v2.4.0
// Two ADS1115 modules, up to eight capacitive soil sensors.
//
#include <WiFi.h>
#include <ESPmDNS.h>
#include <Wire.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <PubSubClient.h>
#include <Adafruit_ADS1X15.h>

#include "secrets.h"

constexpr int SDA_PIN = 21;
constexpr int SCL_PIN = 22;
constexpr uint8_t ADS_PRIMARY_ADDRESS = 0x48;
constexpr uint8_t ADS_SECONDARY_ADDRESS = 0x49;
constexpr uint16_t MQTT_PORT = 1883;
constexpr uint16_t MQTT_BUFFER_SIZE = 512;
constexpr unsigned long PUBLISH_INTERVAL_MS = 5000;
constexpr uint8_t FILTER_SAMPLE_COUNT = 10;
constexpr uint16_t FILTER_SAMPLE_DELAY_MS = 50;
constexpr uint16_t I2C_TIMEOUT_MS = 100;
constexpr unsigned long ADS_READ_TIMEOUT_MS = 100;
constexpr unsigned long ADS_RETRY_INTERVAL_MS = 30000;
constexpr unsigned long ADS_STATUS_INTERVAL_MS = 5000;
constexpr unsigned long MQTT_DISCOVERY_RETRY_MS = 10000;
constexpr uint16_t ADS_SINGLE_ENDED_MUX[] = {
    ADS1X15_REG_CONFIG_MUX_SINGLE_0,
    ADS1X15_REG_CONFIG_MUX_SINGLE_1,
    ADS1X15_REG_CONFIG_MUX_SINGLE_2,
    ADS1X15_REG_CONFIG_MUX_SINGLE_3,
};

const char* MQTT_FALLBACK_BROKER = "192.168.1.99";
const char* MQTT_SERVICE = "mqtt";
const char* MQTT_PROTOCOL = "tcp";
const char* AVORA_DEVICE_ID = "avora-001";
const char* FIRMWARE_VERSION = "2.4.0";
const char* ADS_STATUS_TOPIC = "avora/status/esp32/ads1115";
const char* SENSOR_CONFIG_TOPIC_FILTER =
        "avora/config/esp32/sensors/#";
const char* CALIBRATION_CONFIG_TOPIC_FILTER =
        "avora/config/esp32/calibration/#";
const char* SENSOR_CONFIG_TOPIC_PREFIX =
        "avora/config/esp32/sensors/";
const char* CALIBRATION_CONFIG_TOPIC_PREFIX =
        "avora/config/esp32/calibration/";

Adafruit_ADS1115 adsPrimary;
Adafruit_ADS1115 adsSecondary;
WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

bool adsAvailable[] = {false, false};
unsigned long lastAdsRetryMillis[] = {0, 0};
unsigned long lastAdsStatusMillis = 0;
unsigned long lastPublishMillis = 0;
unsigned long lastMqttDiscoveryMillis = 0;
bool mdnsStarted = false;
bool mqttServerConfigured = false;
IPAddress activeMqttBroker;
uint16_t activeMqttPort = MQTT_PORT;

struct SensorConfig {
    const char* id;
    uint8_t adsIndex;
    uint8_t channel;
    bool enabled;
    int16_t dryRaw;
    int16_t wetRaw;
};

// Enable a slot only after its sensor is physically connected.  This prevents
// floating ADS1115 inputs from being mistaken for real, online soil sensors.
SensorConfig sensors[] = {
    {"soil-001", 0, 0, true, 12650, 505},
    {"soil-002", 0, 1, true, 12650, 505},
    {"soil-003", 0, 2, false, 12650, 505},
    {"soil-004", 0, 3, false, 12650, 505},
    {"soil-005", 1, 0, false, 12650, 505},
    {"soil-006", 1, 1, false, 12650, 505},
    {"soil-007", 1, 2, false, 12650, 505},
    {"soil-008", 1, 3, false, 12650, 505},
};

constexpr size_t SENSOR_COUNT = sizeof(sensors) / sizeof(sensors[0]);

void setSensorEnabled(const char* sensorId, bool enabled) {
    for (SensorConfig& sensor : sensors) {
        if (strcmp(sensor.id, sensorId) != 0) {
            continue;
        }

        sensor.enabled = enabled;
        Serial.print("Sensor ayari guncellendi: ");
        Serial.print(sensor.id);
        Serial.print(" = ");
        Serial.println(enabled ? "ACIK" : "KAPALI");
        return;
    }

    Serial.print("Bilinmeyen sensor ayari yok sayildi: ");
    Serial.println(sensorId);
}

void setSensorCalibration(
        const char* sensorId,
        int16_t dryRaw,
        int16_t wetRaw
) {
    if (dryRaw <= wetRaw) {
        Serial.println("Gecersiz kalibrasyon: kuru deger islak degerden buyuk olmali.");
        return;
    }

    for (SensorConfig& sensor : sensors) {
        if (strcmp(sensor.id, sensorId) != 0) {
            continue;
        }

        sensor.dryRaw = dryRaw;
        sensor.wetRaw = wetRaw;
        Serial.print("Kalibrasyon guncellendi: ");
        Serial.print(sensor.id);
        Serial.print(" kuru=");
        Serial.print(dryRaw);
        Serial.print(" islak=");
        Serial.println(wetRaw);
        return;
    }

    Serial.print("Bilinmeyen sensor kalibrasyonu yok sayildi: ");
    Serial.println(sensorId);
}

bool parseCalibration(
        const byte* payload,
        unsigned int length,
        int16_t& dryRaw,
        int16_t& wetRaw
) {
    if (length == 0 || length >= 32) {
        return false;
    }

    char text[32];
    memcpy(text, payload, length);
    text[length] = '\0';

    char* separator = strchr(text, ',');
    if (separator == nullptr) {
        return false;
    }

    *separator = '\0';
    char* end = nullptr;
    long dry = strtol(text, &end, 10);
    if (*end != '\0' || dry < INT16_MIN || dry > INT16_MAX) {
        return false;
    }

    long wet = strtol(separator + 1, &end, 10);
    if (*end != '\0' || wet < INT16_MIN || wet > INT16_MAX) {
        return false;
    }

    dryRaw = static_cast<int16_t>(dry);
    wetRaw = static_cast<int16_t>(wet);
    return dryRaw > wetRaw;
}

// Retained MQTT command examples:
// avora/config/esp32/sensors/soil-003     -> 1 (enable) / 0 (disable)
// avora/config/esp32/calibration/soil-003 -> 12480,620 (dry,wet)
void onMqttMessage(char* topic, byte* payload, unsigned int length) {
    size_t sensorPrefixLength = strlen(SENSOR_CONFIG_TOPIC_PREFIX);
    size_t calibrationPrefixLength = strlen(CALIBRATION_CONFIG_TOPIC_PREFIX);

    if (strncmp(topic, SENSOR_CONFIG_TOPIC_PREFIX, sensorPrefixLength) == 0) {
        const char* sensorId = topic + sensorPrefixLength;
        if (*sensorId == '\0' || length != 1) {
            Serial.println("Gecersiz sensor ayari MQTT mesaji yok sayildi.");
            return;
        }

        char value = static_cast<char>(payload[0]);
        if (value != '0' && value != '1') {
            Serial.println("Sensor ayari sadece 0 veya 1 olabilir.");
            return;
        }

        setSensorEnabled(sensorId, value == '1');
        return;
    }

    if (strncmp(topic, CALIBRATION_CONFIG_TOPIC_PREFIX, calibrationPrefixLength) != 0) {
        Serial.println("Bilinmeyen MQTT ayar konusu yok sayildi.");
        return;
    }

    const char* sensorId = topic + calibrationPrefixLength;
    int16_t dryRaw;
    int16_t wetRaw;
    if (*sensorId == '\0' || !parseCalibration(payload, length, dryRaw, wetRaw)) {
        Serial.println("Gecersiz kalibrasyon mesaji yok sayildi.");
        return;
    }

    setSensorCalibration(sensorId, dryRaw, wetRaw);
}

int calculateMoisturePercent(int16_t raw, const SensorConfig& sensor) {
    float percentage =
            ((sensor.dryRaw - raw)
             / static_cast<float>(sensor.dryRaw - sensor.wetRaw))
            * 100.0f;

    return static_cast<int>(constrain(roundf(percentage), 0.0f, 100.0f));
}

Adafruit_ADS1115& adsFor(uint8_t adsIndex) {
    return adsIndex == 0 ? adsPrimary : adsSecondary;
}

uint8_t adsAddressFor(uint8_t adsIndex) {
    return adsIndex == 0
            ? ADS_PRIMARY_ADDRESS
            : ADS_SECONDARY_ADDRESS;
}

bool isAdsModuleConnected(uint8_t adsIndex) {
    Wire.beginTransmission(adsAddressFor(adsIndex));
    return Wire.endTransmission() == 0;
}

void publishAdsStatus(bool force = false) {
    if (!mqttClient.connected()) {
        return;
    }

    unsigned long now = millis();
    if (!force
            && now - lastAdsStatusMillis < ADS_STATUS_INTERVAL_MS) {
        return;
    }
    lastAdsStatusMillis = now;

    char payload[280];
    snprintf(
            payload,
            sizeof(payload),
            "{\"node_id\":\"avora-soil-esp32\","
            "\"firmware\":\"%s\",\"node_online\":true,"
            "\"primary_available\":%s,"
            "\"secondary_available\":%s,"
            "\"primary_address\":\"0x48\","
            "\"secondary_address\":\"0x49\","
            "\"rssi\":%d,\"uptime\":%lu}",
            FIRMWARE_VERSION,
            adsAvailable[0] ? "true" : "false",
            adsAvailable[1] ? "true" : "false",
            WiFi.RSSI(),
            millis() / 1000
    );

    if (!mqttClient.publish(ADS_STATUS_TOPIC, payload, true)) {
        Serial.println("HATA: ADS1115 durum mesaji gonderilemedi.");
    }
}

bool readSingleEndedWithTimeout(
        Adafruit_ADS1115& ads,
        uint8_t channel,
        int16_t& raw
) {
    if (channel >= 4) {
        return false;
    }

    ads.startADCReading(ADS_SINGLE_ENDED_MUX[channel], false);
    unsigned long startedAt = millis();

    while (!ads.conversionComplete()) {
        if (millis() - startedAt >= ADS_READ_TIMEOUT_MS) {
            return false;
        }
        delay(1);
    }

    raw = ads.getLastConversionResults();
    return true;
}

bool readFilteredRaw(const SensorConfig& sensor, int16_t& filteredRaw) {
    long total = 0;
    int16_t minimum = INT16_MAX;
    int16_t maximum = INT16_MIN;
    Adafruit_ADS1115& ads = adsFor(sensor.adsIndex);

    for (uint8_t sample = 0; sample < FILTER_SAMPLE_COUNT; sample++) {
        int16_t raw = 0;
        if (!readSingleEndedWithTimeout(ads, sensor.channel, raw)) {
            return false;
        }

        total += raw;
        minimum = min(minimum, raw);
        maximum = max(maximum, raw);
        delay(FILTER_SAMPLE_DELAY_MS);
    }

    // Drop the largest and smallest samples so a single electrical spike
    // cannot distort the moisture value.
    if (FILTER_SAMPLE_COUNT >= 3) {
        total -= minimum;
        total -= maximum;
        filteredRaw = static_cast<int16_t>(
                total / (FILTER_SAMPLE_COUNT - 2)
        );
    } else {
        filteredRaw = static_cast<int16_t>(
                total / FILTER_SAMPLE_COUNT
        );
    }

    return true;
}

bool hasUsableAddress(const IPAddress& address) {
    return address[0] != 0 || address[1] != 0
            || address[2] != 0 || address[3] != 0;
}

void configureMqttServer(
        const IPAddress& address,
        uint16_t port,
        const char* source
) {
    if (!hasUsableAddress(address) || port == 0) {
        return;
    }
    if (mqttServerConfigured
            && activeMqttBroker == address
            && activeMqttPort == port) {
        return;
    }

    activeMqttBroker = address;
    activeMqttPort = port;
    mqttClient.setServer(activeMqttBroker, activeMqttPort);
    mqttServerConfigured = true;

    Serial.print("MQTT sunucusu ");
    Serial.print(source);
    Serial.print(" ile ayarlandi: ");
    Serial.print(activeMqttBroker);
    Serial.print(":");
    Serial.println(activeMqttPort);
}

void configureFallbackMqttServer() {
    IPAddress fallbackAddress;
    if (!fallbackAddress.fromString(MQTT_FALLBACK_BROKER)) {
        Serial.println("HATA: MQTT geri donus adresi gecersiz.");
        return;
    }
    configureMqttServer(fallbackAddress, MQTT_PORT, "geri donus adresi");
}

bool startMdns() {
    if (mdnsStarted) {
        return true;
    }

    String hostname = "avora-sensors-" +
            String(static_cast<uint32_t>(ESP.getEfuseMac()), HEX);
    if (!MDNS.begin(hostname.c_str())) {
        Serial.println("HATA: mDNS istemcisi baslatilamadi.");
        return false;
    }
    mdnsStarted = true;
    Serial.print("mDNS istemcisi baslatildi: ");
    Serial.print(hostname);
    Serial.println(".local");
    return true;
}

bool discoverMqttServer() {
    if (!startMdns()) {
        return false;
    }

    Serial.println("AVORA MQTT sunucusu mDNS ile araniyor...");
    int serviceCount = MDNS.queryService(MQTT_SERVICE, MQTT_PROTOCOL);
    for (int index = 0; index < serviceCount; index++) {
        if (!MDNS.hasTxt(index, "device")
                || MDNS.txt(index, "device") != AVORA_DEVICE_ID) {
            continue;
        }

        IPAddress address = MDNS.address(index);
        uint16_t port = MDNS.port(index);
        if (!hasUsableAddress(address) || port == 0) {
            continue;
        }

        configureMqttServer(address, port, "mDNS kesfi");
        return true;
    }

    Serial.println("AVORA MQTT mDNS ilani bulunamadi.");
    return false;
}

void refreshMqttServer() {
    lastMqttDiscoveryMillis = millis();
    if (!discoverMqttServer() && !mqttServerConfigured) {
        configureFallbackMqttServer();
    }
}

void connectToWiFi() {
    if (WiFi.status() == WL_CONNECTED) {
        return;
    }

    if (mdnsStarted) {
        MDNS.end();
        mdnsStarted = false;
    }
    lastMqttDiscoveryMillis = 0;

    Serial.print("Wi-Fi baglantisi kuruluyor");
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    while (WiFi.status() != WL_CONNECTED) {
        Serial.print(".");
        delay(500);
    }

    Serial.println("\nWi-Fi baglantisi kuruldu.");
    Serial.print("IP adresi: ");
    Serial.println(WiFi.localIP());
}

void connectToMqtt() {
    while (!mqttClient.connected()) {
        if (WiFi.status() != WL_CONNECTED) {
            return;
        }

        unsigned long now = millis();
        if (!mqttServerConfigured
                || lastMqttDiscoveryMillis == 0
                || now - lastMqttDiscoveryMillis >= MQTT_DISCOVERY_RETRY_MS) {
            refreshMqttServer();
        }
        if (!mqttServerConfigured) {
            delay(1000);
            continue;
        }

        String clientId =
                "avora-esp32-" +
                String(static_cast<uint32_t>(ESP.getEfuseMac()), HEX);

        const char* offlineStatus =
                "{\"node_id\":\"avora-soil-esp32\","
                "\"firmware\":\"\",\"node_online\":false,"
                "\"primary_available\":false,"
                "\"secondary_available\":false,"
                "\"primary_address\":\"0x48\","
                "\"secondary_address\":\"0x49\","
                "\"rssi\":0,\"uptime\":0}";
        if (mqttClient.connect(
                clientId.c_str(),
                nullptr,
                nullptr,
                ADS_STATUS_TOPIC,
                0,
                true,
                offlineStatus
        )) {
            Serial.println("MQTT baglantisi kuruldu.");
            if (!mqttClient.subscribe(SENSOR_CONFIG_TOPIC_FILTER)) {
                Serial.println("HATA: Sensor ayar konusuna abone olunamadi.");
            } else {
                Serial.println("Sensor ayarlari dinleniyor.");
            }
            if (!mqttClient.subscribe(CALIBRATION_CONFIG_TOPIC_FILTER)) {
                Serial.println("HATA: Kalibrasyon konusuna abone olunamadi.");
            } else {
                Serial.println("Kalibrasyon ayarlari dinleniyor.");
            }
            publishAdsStatus(true);
            return;
        }

        Serial.print("MQTT hatasi, kod=");
        Serial.println(mqttClient.state());
        delay(5000);
    }
}

void markAdsUnavailable(uint8_t adsIndex, const char* sensorId) {
    bool wasAvailable = adsAvailable[adsIndex];
    adsAvailable[adsIndex] = false;
    lastAdsRetryMillis[adsIndex] = millis();

    if (!wasAvailable) {
        return;
    }

    Serial.print("HATA: ADS1115 #");
    Serial.print(adsIndex + 1);
    Serial.print(" okuma zaman asimi; sensor=");
    Serial.print(sensorId);
    Serial.println(". Diger ADS modulu calismaya devam edecek.");
    publishAdsStatus(true);
}

void publishSensorData(const SensorConfig& sensor) {
    int16_t raw = 0;
    if (!readFilteredRaw(sensor, raw)) {
        markAdsUnavailable(sensor.adsIndex, sensor.id);
        return;
    }

    float voltage = adsFor(sensor.adsIndex).computeVolts(raw);
    int moisture = calculateMoisturePercent(raw, sensor);

    char topic[64];
    char payload[240];
    snprintf(topic, sizeof(topic), "avora/sensors/%s", sensor.id);
    snprintf(
            payload,
            sizeof(payload),
            "{\"sensor_id\":\"%s\",\"firmware\":\"%s\","
            "\"raw\":%d,\"voltage\":%.3f,"
            "\"moisture\":%d,\"rssi\":%d,\"uptime\":%lu}",
            sensor.id,
            FIRMWARE_VERSION,
            raw,
            voltage,
            moisture,
            WiFi.RSSI(),
            millis() / 1000
    );

    if (mqttClient.publish(topic, payload, false)) {
        Serial.print("MQTT gonderildi: ");
        Serial.println(payload);
    } else {
        Serial.print("MQTT gonderilemedi: ");
        Serial.println(sensor.id);
    }
}

bool initializeAdsModule(uint8_t adsIndex) {
    uint8_t address = adsAddressFor(adsIndex);
    Adafruit_ADS1115& ads = adsFor(adsIndex);

    bool found = ads.begin(address, &Wire);
    adsAvailable[adsIndex] = found;
    lastAdsRetryMillis[adsIndex] = millis();

    Serial.print("ADS1115 #");
    Serial.print(adsIndex + 1);

    if (found) {
        ads.setGain(GAIN_ONE);
        Serial.print(" bulundu: 0x");
        Serial.println(address, HEX);
        publishAdsStatus(true);
        return true;
    }

    Serial.print(" bulunamadi: 0x");
    Serial.print(address, HEX);
    Serial.println("; ilgili sensor kanallari pasif, MQTT devam ediyor.");
    publishAdsStatus(true);
    return false;
}

void initializeAds() {
    initializeAdsModule(0);
    initializeAdsModule(1);
}

void retryUnavailableAds(unsigned long now) {
    for (uint8_t adsIndex = 0; adsIndex < 2; adsIndex++) {
        if (adsAvailable[adsIndex]) {
            if (!isAdsModuleConnected(adsIndex)) {
                markAdsUnavailable(adsIndex, "I2C baglantisi");
            }
            continue;
        }
        if (now - lastAdsRetryMillis[adsIndex] < ADS_RETRY_INTERVAL_MS) {
            continue;
        }

        Serial.print("ADS1115 yeniden deneniyor: #");
        Serial.println(adsIndex + 1);
        initializeAdsModule(adsIndex);
    }
}

void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("AVORA 8 kanalli sensor baslatiliyor...");

    Wire.begin(SDA_PIN, SCL_PIN);
    Wire.setTimeOut(I2C_TIMEOUT_MS);
    initializeAds();
    connectToWiFi();
    refreshMqttServer();
    mqttClient.setBufferSize(MQTT_BUFFER_SIZE);
    mqttClient.setCallback(onMqttMessage);
    connectToMqtt();
}

void loop() {
    if (WiFi.status() != WL_CONNECTED) {
        connectToWiFi();
    }
    if (!mqttClient.connected()) {
        connectToMqtt();
    }
    mqttClient.loop();

    unsigned long now = millis();
    retryUnavailableAds(now);
    publishAdsStatus();
    if (now - lastPublishMillis < PUBLISH_INTERVAL_MS) {
        return;
    }
    lastPublishMillis = now;

    for (const SensorConfig& sensor : sensors) {
        if (!sensor.enabled || !adsAvailable[sensor.adsIndex]) {
            continue;
        }
        publishSensorData(sensor);
    }
}
