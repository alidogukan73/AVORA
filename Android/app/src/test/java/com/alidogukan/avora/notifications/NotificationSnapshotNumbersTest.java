package com.alidogukan.avora.notifications;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NodeUtilities;
import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class NotificationSnapshotNumbersTest {
    private DataSnapshot snapshot(Object value) {
        try {
            java.lang.reflect.Constructor<DataSnapshot> constructor = DataSnapshot.class
                    .getDeclaredConstructor(DatabaseReference.class, IndexedNode.class);
            constructor.setAccessible(true);
            return constructor.newInstance(null, IndexedNode.from(NodeUtilities.NodeFromJSON(value)));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @Test public void sdkRejectsOldAbstractNumberMapping() {
        assertThrows(DatabaseException.class, () -> snapshot(24.5).getValue(Number.class));
    }

    @Test public void forecastSupportsBothIntegerAndDecimalLeaves() {
        assertEquals(24.5, NotificationSnapshotNumbers.number(snapshot(24.5)), 0.0);
        assertEquals(80.0, NotificationSnapshotNumbers.number(snapshot(80L)), 0.0);
    }

    @Test public void timestampsKeepTheirLongPrecision() {
        assertEquals(1791709200L, NotificationSnapshotNumbers.epoch(snapshot(1791709200L)));
        assertEquals(1791709200L, NotificationSnapshotNumbers.epoch(snapshot(1791709200.0)));
    }

    @Test public void missingOrInvalidLeavesDoNotAbortOtherNotifications() {
        for (Object value : new Object[]{null, "unknown", true, Map.of("value", 25)}) {
            assertNull(NotificationSnapshotNumbers.number(snapshot(value)));
            assertEquals(0L, NotificationSnapshotNumbers.epoch(snapshot(value)));
        }
    }
}
