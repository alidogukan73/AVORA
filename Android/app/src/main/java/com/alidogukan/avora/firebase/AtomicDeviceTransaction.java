package com.alidogukan.avora.firebase;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import java.util.function.Consumer;

/** Waits for the server's device snapshot before applying an atomic mutation. */
final class AtomicDeviceTransaction implements Transaction.Handler {
    private final String fallback;
    private final Consumer<MutableData> mutation;
    private final Consumer<Exception> completion;
    private volatile boolean applied;
    private volatile String failure;

    AtomicDeviceTransaction(String fallback, Consumer<MutableData> mutation,
                            Consumer<Exception> completion) {
        this.fallback = fallback;
        this.failure = fallback;
        this.mutation = mutation;
        this.completion = completion;
    }

    @Override public Transaction.Result doTransaction(MutableData currentData) {
        applied = false;
        failure = fallback;
        // Child listeners do not populate a complete device-root cache. Firebase
        // may first supply null even though products and zones exist on the server.
        // Sending the unchanged empty value lets its compare-and-retry fetch the
        // real snapshot; aborting here would never reach the server-side data.
        if (currentData.getValue() == null) return Transaction.success(currentData);
        try {
            mutation.accept(currentData);
            applied = true;
            return Transaction.success(currentData);
        } catch (RuntimeException error) {
            if (error.getMessage() != null && !error.getMessage().isBlank()) {
                failure = error.getMessage();
            }
            return Transaction.abort();
        }
    }

    @Override public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
        if (error != null) completion.accept(new DatabaseWriteException(error));
        else if (!committed || !applied || snapshot == null || !snapshot.exists()) {
            // A genuinely absent device can acknowledge an empty no-op. That is
            // not a saved fertilizer record and must never be reported as success.
            completion.accept(new IllegalStateException(failure));
        } else completion.accept(null);
    }
}
