from __future__ import annotations

import json
import logging
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

import paho.mqtt.client as mqtt

from hardware.mqtt_security import configure_mqtt_credentials


logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class MqttSensorReading:

    sensor_id: str
    raw: int
    voltage: float
    moisture: int
    rssi: int

    firmware: str = ""
    uptime_seconds: int = 0

    received_at: datetime = field(
        default_factory=datetime.now
    )

    received_monotonic: float = field(
        default_factory=time.monotonic
    )

    @property
    def age_seconds(self) -> float:
        """
        Ölçümün Raspberry Pi tarafından alınmasının üzerinden
        geçen süreyi döndürür.

        Sistem saatinden etkilenmemesi için monotonic saat kullanılır.
        """
        return max(
            0.0,
            time.monotonic() - self.received_monotonic,
        )


@dataclass(frozen=True)
class MqttAds1115Status:

    node_online: bool
    primary_available: bool
    secondary_available: bool
    rssi: int
    firmware: str = ""
    uptime_seconds: int = 0
    received_at: datetime = field(
        default_factory=datetime.now
    )
    received_monotonic: float = field(
        default_factory=time.monotonic
    )

    @property
    def age_seconds(self) -> float:
        return max(
            0.0,
            time.monotonic() - self.received_monotonic,
        )


class MqttSoilMoistureSensor:
    """
    ESP32 kablosuz toprak nemi sensörünü MQTT üzerinden dinler.

    MQTT mesajları arka planda alınır. Son geçerli ölçüm,
    thread-safe biçimde bellekte tutulur.
    """

    def __init__(
        self,
        broker: str = "127.0.0.1",
        port: int = 1883,
        topic: str = "avora/sensors/soil-001",
        ads_status_topic: str = "avora/status/esp32/ads1115",
        sensor_id: str = "soil-001",
        stale_after_seconds: float = 30.0,
        client_id: str = "avora-pi-wireless-sensor",
        username: str | None = None,
        password: str | None = None,
    ) -> None:
        if not broker:
            raise ValueError("MQTT broker adresi boş olamaz.")

        if not 1 <= port <= 65535:
            raise ValueError(
                f"Geçersiz MQTT portu: {port}"
            )

        if not topic:
            raise ValueError("MQTT konusu boş olamaz.")

        if not sensor_id:
            raise ValueError("Sensör kimliği boş olamaz.")

        if stale_after_seconds <= 0:
            raise ValueError(
                "stale_after_seconds sıfırdan büyük olmalıdır."
            )

        if not ads_status_topic:
            raise ValueError("ADS1115 durum konusu boş olamaz.")

        self._broker = broker
        self._port = port
        self._topic = topic
        self._ads_status_topic = ads_status_topic
        self._sensor_id = sensor_id
        self._stale_after_seconds = stale_after_seconds
        self._client_id = client_id

        self._reading_lock = threading.Lock()
        self._state_lock = threading.Lock()

        self._latest_readings: dict[
            str,
            MqttSensorReading,
        ] = {}
        self._latest_ads_status: MqttAds1115Status | None = None
        self._is_connected = False
        self._is_started = False

        self._client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=self._client_id,
            protocol=mqtt.MQTTv311,
        )
        self._authenticated = configure_mqtt_credentials(
            self._client,
            username,
            password,
        )

        self._client.on_connect = self._on_connect
        self._client.on_disconnect = self._on_disconnect
        self._client.on_message = self._on_message

        self._client.reconnect_delay_set(
            min_delay=1,
            max_delay=30,
        )

    @property
    def broker(self) -> str:
        return self._broker

    @property
    def port(self) -> int:
        return self._port

    @property
    def topic(self) -> str:
        return self._topic

    @property
    def ads_status_topic(self) -> str:
        return self._ads_status_topic

    @property
    def expected_sensor_id(self) -> str:
        return self._sensor_id

    @property
    def stale_after_seconds(self) -> float:
        return self._stale_after_seconds

    @property
    def is_connected(self) -> bool:
        with self._state_lock:
            return self._is_connected

    @property
    def is_started(self) -> bool:
        with self._state_lock:
            return self._is_started

    def start(self) -> None:
        """
        MQTT bağlantısını ve arka plan dinleme döngüsünü başlatır.
        """

        with self._state_lock:
            if self._is_started:
                logger.debug(
                    "MQTT sensör dinleyicisi zaten çalışıyor."
                )
                return

            self._is_started = True

        logger.info(
            "Kablosuz sensör başlatılıyor: broker=%s:%s topic=%s authenticated=%s",
            self._broker,
            self._port,
            self._topic,
            self._authenticated,
        )

        try:
            self._client.connect(
                self._broker,
                self._port,
                keepalive=60,
            )

            self._client.loop_start()

        except Exception:
            with self._state_lock:
                self._is_started = False
                self._is_connected = False

            logger.exception(
                "MQTT broker bağlantısı başlatılamadı."
            )
            raise

    def stop(self) -> None:
        """
        Arka plan MQTT döngüsünü güvenli biçimde durdurur.
        """

        with self._state_lock:
            if not self._is_started:
                return

            self._is_started = False

        logger.info(
            "Kablosuz sensör durduruluyor."
        )

        try:
            self._client.disconnect()
        finally:
            self._client.loop_stop()

            with self._state_lock:
                self._is_connected = False

    def get_latest_reading(
        self,
        sensor_id: str | None = None,
    ) -> MqttSensorReading | None:
        """
        Son alınan ölçümü döndürür.

        Ölçüm henüz gelmediyse None döner.
        Eski ölçümü de döndürebilir; güncellik için
        is_reading_fresh() kullanılmalıdır.
        """

        requested_sensor_id = (
            sensor_id
            or self._sensor_id
        )

        with self._reading_lock:
            return self._latest_readings.get(
                requested_sensor_id,
            )

    def get_latest_readings(
        self,
    ) -> dict[str, MqttSensorReading]:
        """
        Return a snapshot of all known sensor readings.
        """

        with self._reading_lock:
            return dict(
                self._latest_readings,
            )

    def get_fresh_reading(
        self,
        sensor_id: str | None = None,
    ) -> MqttSensorReading | None:
        """
        Yalnızca güncel ölçümü döndürür.

        Ölçüm yoksa veya zaman aşımına uğramışsa None döner.
        """

        reading = self.get_latest_reading(
            sensor_id,
        )

        if reading is None:
            return None

        if reading.age_seconds > self._stale_after_seconds:
            return None

        return reading

    def get_fresh_readings(
        self,
    ) -> dict[str, MqttSensorReading]:
        """
        Return fresh readings from every known sensor.
        """

        return {
            sensor_id: reading
            for sensor_id, reading
            in self.get_latest_readings().items()
            if (
                reading.age_seconds
                <= self._stale_after_seconds
            )
        }

    def get_latest_ads1115_status(self) -> MqttAds1115Status | None:
        with self._reading_lock:
            return self._latest_ads_status

    def is_reading_fresh(self) -> bool:
        """
        Son ölçümün kullanılabilecek kadar güncel olup
        olmadığını döndürür.
        """

        return self.get_fresh_reading() is not None

    def wait_for_reading(
        self,
        timeout_seconds: float = 15.0,
    ) -> MqttSensorReading | None:
        """
        İlk güncel MQTT ölçümünü belirli süre bekler.

        Süre dolarsa None döner.
        """

        if timeout_seconds <= 0:
            raise ValueError(
                "timeout_seconds sıfırdan büyük olmalıdır."
            )

        deadline = time.monotonic() + timeout_seconds

        while time.monotonic() < deadline:
            reading = self.get_fresh_reading()

            if reading is not None:
                return reading

            time.sleep(0.1)

        return None

    def _on_connect(
        self,
        client: mqtt.Client,
        userdata: Any,
        flags: mqtt.ConnectFlags,
        reason_code: mqtt.ReasonCode,
        properties: mqtt.Properties | None,
    ) -> None:
        if reason_code != 0:
            with self._state_lock:
                self._is_connected = False

            logger.error(
                "MQTT broker bağlantısı başarısız: %s",
                reason_code,
            )
            return

        with self._state_lock:
            self._is_connected = True

        logger.info(
            "MQTT broker bağlantısı kuruldu."
        )

        result, message_id = client.subscribe(
            self._topic,
            qos=0,
        )

        if result != mqtt.MQTT_ERR_SUCCESS:
            logger.error(
                "MQTT konusuna abone olunamadı: result=%s",
                result,
            )
            return

        logger.info(
            "Kablosuz sensör konusu dinleniyor: "
            "topic=%s message_id=%s",
            self._topic,
            message_id,
        )

        status_result, status_message_id = client.subscribe(
            self._ads_status_topic,
            qos=0,
        )
        if status_result != mqtt.MQTT_ERR_SUCCESS:
            logger.error(
                "ADS1115 durum konusuna abone olunamadı: result=%s",
                status_result,
            )
            return
        logger.info(
            "ADS1115 durum konusu dinleniyor: topic=%s message_id=%s",
            self._ads_status_topic,
            status_message_id,
        )

    def _on_disconnect(
        self,
        client: mqtt.Client,
        userdata: Any,
        disconnect_flags: mqtt.DisconnectFlags,
        reason_code: mqtt.ReasonCode,
        properties: mqtt.Properties | None,
    ) -> None:
        with self._state_lock:
            self._is_connected = False

        if reason_code == 0:
            logger.info(
                "MQTT bağlantısı kapatıldı."
            )
        else:
            logger.warning(
                "MQTT bağlantısı beklenmedik şekilde koptu: %s",
                reason_code,
            )

    def _on_message(
        self,
        client: mqtt.Client,
        userdata: Any,
        message: mqtt.MQTTMessage,
    ) -> None:
        if message.topic == self._ads_status_topic:
            try:
                status = self._parse_ads_status_payload(message.payload)
            except (ValueError, TypeError) as exc:
                logger.warning(
                    "Geçersiz ADS1115 durum mesajı: %s; payload=%r",
                    exc,
                    message.payload,
                )
                return

            with self._reading_lock:
                self._latest_ads_status = status
            logger.debug(
                "ADS1115 durumu alındı: online=%s primary=%s secondary=%s",
                status.node_online,
                status.primary_available,
                status.secondary_available,
            )
            return

        try:
            reading = self._parse_payload(
                message.payload
            )
        except (ValueError, TypeError) as exc:
            logger.warning(
                "Geçersiz kablosuz sensör mesajı: %s; payload=%r",
                exc,
                message.payload,
            )
            return

        topic_sensor_id = (
            message.topic
            .rsplit("/", maxsplit=1)[-1]
            .strip()
        )

        if (
            topic_sensor_id
            and topic_sensor_id != reading.sensor_id
        ):
            logger.warning(
                "Beklenmeyen sensör kimliği yok sayıldı: "
                "beklenen=%s gelen=%s",
                message.topic,
                reading.sensor_id,
            )
            return

        with self._reading_lock:
            self._latest_readings[
                reading.sensor_id
            ] = reading

        logger.debug(
            "Kablosuz sensör ölçümü alındı: "
            "sensor_id=%s raw=%s voltage=%.3f "
            "moisture=%s rssi=%s",
            reading.sensor_id,
            reading.raw,
            reading.voltage,
            reading.moisture,
            reading.rssi,
        )

    @staticmethod
    def _parse_payload(
        payload: bytes,
    ) -> MqttSensorReading:
        try:
            payload_text = payload.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise ValueError(
                "MQTT mesajı UTF-8 formatında değil."
            ) from exc

        try:
            data = json.loads(payload_text)
        except json.JSONDecodeError as exc:
            raise ValueError(
                "MQTT mesajı geçerli JSON değil."
            ) from exc

        if not isinstance(data, dict):
            raise ValueError(
                "MQTT mesajının JSON nesnesi olması gerekir."
            )

        required_fields = {
            "sensor_id",
            "raw",
            "voltage",
            "moisture",
            "rssi",
        }

        missing_fields = required_fields.difference(
            data.keys()
        )

        if missing_fields:
            missing_text = ", ".join(
                sorted(missing_fields)
            )

            raise ValueError(
                f"Eksik alanlar: {missing_text}"
            )

        try:
            sensor_id = str(data["sensor_id"]).strip()
            raw = int(data["raw"])
            voltage = float(data["voltage"])
            moisture = int(data["moisture"])
            rssi = int(data["rssi"])
            firmware = str(
                data.get(
                    "firmware",
                    ""
                )
            )

            uptime_seconds = int(
                data.get(
                    "uptime",
                    0
                )
            )

        except (TypeError, ValueError) as exc:
            raise ValueError(
                "MQTT sensör alanlarından biri "
                "geçersiz veri tipinde."
            ) from exc

        if not sensor_id:
            raise ValueError(
                "sensor_id boş olamaz."
            )

        if not 0 <= raw <= 32767:
            raise ValueError(
                f"Raw değeri geçersiz: {raw}"
            )

        if not 0.0 <= voltage <= 3.3:
            raise ValueError(
                f"Voltaj değeri geçersiz: {voltage}"
            )

        if not 0 <= moisture <= 100:
            raise ValueError(
                f"Nem değeri geçersiz: {moisture}"
            )

        if not -120 <= rssi <= 0:
            raise ValueError(
                f"Wi-Fi RSSI değeri geçersiz: {rssi}"
            )

        return MqttSensorReading(
            sensor_id=sensor_id,
            raw=raw,
            voltage=voltage,
            moisture=moisture,
            rssi=rssi,

            firmware=firmware,
            uptime_seconds=uptime_seconds,

            received_at=datetime.now().astimezone(),
            received_monotonic=time.monotonic(),
        )

    @staticmethod
    def _parse_ads_status_payload(payload: bytes) -> MqttAds1115Status:
        try:
            data = json.loads(payload.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ValueError("ADS1115 durum mesajı geçerli JSON değil.") from exc

        if not isinstance(data, dict):
            raise ValueError("ADS1115 durum mesajı JSON nesnesi olmalıdır.")

        required = {
            "node_online",
            "primary_available",
            "secondary_available",
            "rssi",
        }
        missing = sorted(required.difference(data))
        if missing:
            raise ValueError(f"Eksik ADS1115 durum alanları: {', '.join(missing)}")

        for name in (
            "node_online",
            "primary_available",
            "secondary_available",
        ):
            if not isinstance(data[name], bool):
                raise ValueError(f"{name} boolean olmalıdır.")

        try:
            rssi = int(data["rssi"])
            uptime_seconds = int(data.get("uptime", 0))
        except (TypeError, ValueError) as exc:
            raise ValueError("ADS1115 durum sayıları geçersiz.") from exc

        if not -120 <= rssi <= 0:
            raise ValueError("ADS1115 durum RSSI değeri geçersiz.")
        if not 0 <= uptime_seconds <= 4294967:
            raise ValueError("ADS1115 durum çalışma süresi geçersiz.")

        return MqttAds1115Status(
            node_online=data["node_online"],
            primary_available=data["primary_available"],
            secondary_available=data["secondary_available"],
            rssi=rssi,
            firmware=str(data.get("firmware", ""))[:32],
            uptime_seconds=uptime_seconds,
            received_at=datetime.now().astimezone(),
            received_monotonic=time.monotonic(),
        )


def main() -> None:
    """
    Dosya doğrudan çalıştırıldığında basit donanım testi yapar.
    """

    logging.basicConfig(
        level=logging.INFO,
        format=(
            "%(asctime)s | %(levelname)-8s | %(message)s"
        ),
    )

    sensor = MqttSoilMoistureSensor(
        broker="127.0.0.1",
        port=1883,
        topic="avora/sensors/soil-001",
        sensor_id="soil-001",
        stale_after_seconds=15.0,
    )

    try:
        sensor.start()

        print()
        print("İlk kablosuz sensör ölçümü bekleniyor...")

        reading = sensor.wait_for_reading(
            timeout_seconds=15.0
        )

        if reading is None:
            print(
                "HATA: 15 saniye içinde sensör ölçümü alınamadı."
            )
            return

        print()
        print("İlk ölçüm başarıyla alındı:")
        print(f"Sensör ID : {reading.sensor_id}")
        print(f"Raw       : {reading.raw}")
        print(f"Voltaj    : {reading.voltage:.3f} V")
        print(f"Nem       : %{reading.moisture}")
        print(f"Wi-Fi RSSI: {reading.rssi} dBm")
        print()

        print(
            "Canlı ölçümler gösteriliyor. "
            "Çıkmak için Ctrl+C kullan."
        )

        while True:
            reading = sensor.get_fresh_reading()

            if reading is None:
                print(
                    "UYARI: Kablosuz sensör verisi güncel değil."
                )
            else:
                print(
                    f"Raw={reading.raw} "
                    f"Voltage={reading.voltage:.3f} V "
                    f"Moisture={reading.moisture}% "
                    f"RSSI={reading.rssi} dBm "
                    f"Age={reading.age_seconds:.1f}s"
                )

            time.sleep(5)

    except KeyboardInterrupt:
        print("\nTest kullanıcı tarafından durduruldu.")
    finally:
        sensor.stop()


if __name__ == "__main__":
    main()
