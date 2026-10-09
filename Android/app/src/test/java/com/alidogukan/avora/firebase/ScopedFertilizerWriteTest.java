package com.alidogukan.avora.firebase;

import static org.junit.Assert.*;
import com.alidogukan.avora.models.FertilizerProduct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ScopedFertilizerWriteTest {
    private static final long NOW = 1791520000L;
    private static final String OP = "00000000-0000-0000-0000-000000000001";

    static Map<String, Object> fixture(long stock, String status) {
        return Map.of("fertilizer_products", Map.of("product", Map.of("stock_amount", stock, "stock_unit", "g")),
                "zones", Map.of("zone-001", zone(status), "zone-003", zone(status)));
    }

    private static Map<String, Object> zone(String status) {
        return Map.of("season", Map.of("status", status, "active_season_id", "season-current", "updated_at_epoch", NOW - 1),
                "fertilization", Map.of("next_application_at_epoch", 100L));
    }

    private static void apply(FertilizerData data, boolean stock) {
        FertilizerProduct product = new FertilizerProduct();
        product.setProduct_id("product");
        product.setName("Test ürünü");
        product.setMinimum_interval_days(7);
        var applications = List.of(application("zone-001"), application("zone-003"));
        FirebaseRepository.applyFertilizerBatches(data,
                List.of(new FirebaseRepository.FertilizerApplicationBatch(product, applications, "g", stock)),
                List.of(List.of("application-one", "application-three")), NOW);
    }

    private static FirebaseRepository.BulkFertilizerApplication application(String zone) {
        return new FirebaseRepository.BulkFertilizerApplication(zone, zone, 10, 20, 100,
                5, 15, "MANUAL", "", NOW, "NUTRITION");
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            java.util.StringJoiner result = new java.util.StringJoiner(",", "{", "}");
            new java.util.TreeMap<>((Map<String, Object>) value)
                    .forEach((key, item) -> result.add(json(key) + ":" + json(item)));
            return result.toString();
        }
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    @Test public void savesBothZonesAndStockWithoutReadingOrWritingTelemetry() throws Exception {
        var original = fixture(1000, "ACTIVE");
        FertilizerData data = new FertilizerData(original);
        apply(data, true);
        var updates = data.updates(0, OP);
        assertEquals(980.0, updates.get("fertilizer_products/product/stock_amount"));
        assertEquals(10.0, updates.get("fertilizer_history/application-three/applied_dose"));
        assertEquals(NOW + 7 * 86400L, updates.get("zones/zone-001/fertilization/next_application_at_epoch"));
        String json = json(updates);
        assertFalse(json.contains("sensor_history"));
        assertFalse(json.contains("irrigation_status"));
        assertFalse(json.contains("photo_metadata"));
        assertTrue(json.length() < 30000);
        assertEquals(1000L, FertilizerData.read(original, "fertilizer_products/product/stock_amount"));
        // The emulator suite consumes this actual production-generated payload.
        Path output = Path.of("build/test-fixtures/fertilizer-scoped.json");
        Files.createDirectories(output.getParent());
        String fixture = json(Map.of("device", original, "updates", updates));
        Files.writeString(output, fixture);
        assertEquals("Regenerate the rules fixture when the production payload changes", fixture,
                Files.readString(Path.of("../../firebase-rules-tests/fixtures/fertilizer-scoped.json")).trim());
    }

    @Test public void retryRebuildsStockFromFreshDataAndKeepsIds() {
        FertilizerData data = new FertilizerData(fixture(950, "ACTIVE"));
        apply(data, true);
        var updates = data.updates(3, OP);
        assertEquals(930.0, updates.get("fertilizer_products/product/stock_amount"));
        assertTrue(updates.containsKey("fertilizer_history/application-one/applied_dose"));
        assertEquals(4L, ((Map<?, ?>)updates.get("fertilizer_write_guard")).get("revision"));
    }

    @Test public void insufficientStockAndClosedSeasonStillFailBeforeSendingAnything() {
        assertThrows(IllegalStateException.class, () -> apply(new FertilizerData(fixture(5, "ACTIVE")), true));
        assertThrows(IllegalStateException.class, () -> apply(new FertilizerData(fixture(1000, "CLOSED")), false));
    }

    @Test public void stockUncheckedDoesNotWriteStock() {
        FertilizerData data = new FertilizerData(fixture(1000, "ACTIVE"));
        apply(data, false);
        assertFalse(data.updates(0, OP).containsKey("fertilizer_products/product/stock_amount"));
    }

    @Test public void missingDeviceCannotInventASeasonWhenStockIsUnchecked() {
        assertThrows(IllegalStateException.class, () -> apply(new FertilizerData(Map.of()), false));
    }

    @Test public void deletionGuardsOldRecordAndUsesOneNullUpdate() {
        FertilizerData data = new FertilizerData(Map.of("fertilizer_history", Map.of("one", Map.of("applied_dose", 10))));
        data.child("fertilizer_history/one").setValue(null);
        var updates = data.updates(0, OP);
        assertTrue(updates.containsKey("fertilizer_history/one"));
        assertNull(updates.get("fertilizer_history/one"));
        assertTrue(json(updates.get("fertilizer_write_guard")).contains("fertilizer_history/one/applied_dose"));
    }
}
