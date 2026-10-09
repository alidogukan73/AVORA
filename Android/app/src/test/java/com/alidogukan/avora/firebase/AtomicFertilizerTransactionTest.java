package com.alidogukan.avora.firebase;

import static org.junit.Assert.*;

import com.alidogukan.avora.models.FertilizerProduct;
import com.google.firebase.database.*;
import com.google.firebase.database.snapshot.*;
import org.junit.Test;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the production fertilizer mutation against real Firebase snapshot objects. */
public class AtomicFertilizerTransactionTest {
    private static final long NOW = 1791520000L;
    private final AtomicReference<Exception> completion = new AtomicReference<>();

    @Test public void coldCacheWaitsThenSavesBothZonesWithStockAtomically() {
        // The old handler called this immediately and aborted before fetching the server.
        assertThrows(IllegalStateException.class, () -> apply(data(null), true));
        AtomicDeviceTransaction transaction = transaction(true);
        MutableData empty = data(null);
        assertTrue(transaction.doTransaction(empty).isSuccess());
        assertNull(empty.getValue());
        MutableData server = device(1000, "ACTIVE");
        assertTrue(transaction.doTransaction(server).isSuccess());
        assertEquals(980.0, server.child("fertilizer_products/product/stock_amount").getValue(Double.class), 0);
        for (String zone : List.of("zone-001", "zone-003")) {
            assertEquals(10.0, server.child("fertilizer_history/" + zone + "/applied_dose").getValue(Double.class), 0);
            assertEquals(NOW + 7 * 86400L, (long) server.child("zones/" + zone +
                    "/fertilization/next_application_at_epoch").getValue(Long.class));
            assertEquals("season-" + zone, server.child("fertilizer_history/" + zone + "/season_id").getValue());
        }
        transaction.onComplete(null, true, snapshot(server));
        assertNull(completion.get());
    }

    @Test public void coldCacheWithoutStockDoesNotCreateAnEmptyDeviceOrInventSeasons() {
        AtomicDeviceTransaction transaction = transaction(false);
        MutableData empty = data(null);
        assertTrue(transaction.doTransaction(empty).isSuccess());
        assertNull(empty.getValue());
        MutableData server = device(1000, "ACTIVE");
        assertTrue(transaction.doTransaction(server).isSuccess());
        assertEquals(1000L, server.child("fertilizer_products/product/stock_amount").getValue());
        assertEquals("season-zone-003", server.child("fertilizer_history/zone-003/season_id").getValue());
    }

    @Test public void absentServerDeviceCannotReportASavedRecord() {
        AtomicDeviceTransaction transaction = transaction(false);
        MutableData empty = data(null);
        assertTrue(transaction.doTransaction(empty).isSuccess());
        transaction.onComplete(null, true, snapshot(empty));
        assertNotNull(completion.get());
        assertNull(empty.getValue());
    }

    @Test public void retryUsesNewServerStockAndStableRecordIdsWithoutDoubleDeduction() {
        AtomicDeviceTransaction transaction = transaction(true);
        assertTrue(transaction.doTransaction(device(1000, "ACTIVE")).isSuccess());
        MutableData concurrent = device(950, "ACTIVE");
        assertTrue(transaction.doTransaction(concurrent).isSuccess());
        assertEquals(930.0, concurrent.child("fertilizer_products/product/stock_amount").getValue(Double.class), 0);
        assertEquals(2, concurrent.child("fertilizer_history").getChildrenCount());
    }

    @Test public void insufficientStockAbortsWithoutHistoryOrSchedule() {
        AtomicDeviceTransaction transaction = transaction(true);
        MutableData server = device(5, "ACTIVE");
        assertFalse(transaction.doTransaction(server).isSuccess());
        assertFalse(server.hasChild("fertilizer_history"));
        assertEquals(5L, server.child("fertilizer_products/product/stock_amount").getValue());
        transaction.onComplete(null, false, snapshot(server));
        assertEquals("Gübre stoğu bu uygulama için yetersiz.", completion.get().getMessage());
    }

    @Test public void closedSeasonStillRejectsTheWholeTransaction() {
        AtomicDeviceTransaction transaction = transaction(false);
        MutableData server = device(1000, "CLOSED");
        assertFalse(transaction.doTransaction(server).isSuccess());
        assertFalse(server.hasChild("fertilizer_history"));
        transaction.onComplete(null, false, snapshot(server));
        assertTrue(completion.get().getMessage().contains("sezonu kapalı"));
    }

    @Test public void serverPermissionFailureIsNotSuccessAfterLocalMutation() {
        AtomicDeviceTransaction transaction = transaction(true);
        MutableData server = device(1000, "ACTIVE");
        assertTrue(transaction.doTransaction(server).isSuccess());
        transaction.onComplete(DatabaseError.fromCode(DatabaseError.PERMISSION_DENIED), false, snapshot(server));
        assertEquals(DatabaseError.PERMISSION_DENIED, ((DatabaseWriteException) completion.get()).getCode());
    }

    @Test public void deletionDuringRetryDoesNotKeepThePreviousAppliedFlag() {
        AtomicDeviceTransaction transaction = transaction(true);
        assertTrue(transaction.doTransaction(device(1000, "ACTIVE")).isSuccess());
        MutableData empty = data(null);
        assertTrue(transaction.doTransaction(empty).isSuccess());
        transaction.onComplete(null, true, snapshot(empty));
        assertNotNull(completion.get());
    }

    private AtomicDeviceTransaction transaction(boolean stock) {
        return new AtomicDeviceTransaction("Kaydedilemedi", root -> apply(root, stock), completion::set);
    }

    private void apply(MutableData root, boolean stock) {
        FertilizerProduct product = new FertilizerProduct();
        product.setProduct_id("product");
        product.setName("Test ürünü");
        product.setMinimum_interval_days(7);
        List<FirebaseRepository.BulkFertilizerApplication> applications = List.of(application("zone-001"), application("zone-003"));
        FirebaseRepository.applyFertilizerBatches(root,
                List.of(new FirebaseRepository.FertilizerApplicationBatch(product, applications, "g", stock)),
                List.of(List.of("zone-001", "zone-003")), NOW);
    }

    private FirebaseRepository.BulkFertilizerApplication application(String zone) {
        return new FirebaseRepository.BulkFertilizerApplication(zone, zone, 10, 20, 100,
                5, 15, "MANUAL", "", NOW, "NUTRITION");
    }

    private MutableData device(long stock, String status) {
        return data(Map.of("fertilizer_products", Map.of("product", Map.of("stock_amount", stock, "stock_unit", "g")),
                "zones", Map.of("zone-001", zone("zone-001", status), "zone-003", zone("zone-003", status))));
    }

    private Map<String, Object> zone(String id, String status) {
        return Map.of("season", Map.of("status", status, "active_season_id", "season-" + id),
                "fertilization", Map.of("next_application_at_epoch", 100L));
    }

    private MutableData data(Object value) {
        return InternalHelpers.createMutableData(NodeUtilities.NodeFromJSON(value));
    }

    private DataSnapshot snapshot(MutableData value) {
        return InternalHelpers.createDataSnapshot(null, IndexedNode.from(NodeUtilities.NodeFromJSON(value.getValue())));
    }
}
