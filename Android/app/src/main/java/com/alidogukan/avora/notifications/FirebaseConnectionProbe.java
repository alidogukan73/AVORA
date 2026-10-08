package com.alidogukan.avora.notifications;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Waits for Firebase's live transport signal.
 * <p>The first value of .info/connected can be false while a freshly started
 * process is still opening its socket. A one-shot get therefore cannot
 * distinguish startup from a real network loss.</p>
 */
public final class FirebaseConnectionProbe {
    private FirebaseConnectionProbe() { }

    public static boolean awaitConnected(long timeout, TimeUnit unit)
            throws InterruptedException {
        CountDownLatch signal = new CountDownLatch(1);
        AtomicBoolean connected = new AtomicBoolean(false);
        DatabaseReference reference = FirebaseDatabase.getInstance()
                .getReference(".info/connected");

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (Boolean.TRUE.equals(snapshot.getValue(Boolean.class))) {
                    connected.set(true);
                    signal.countDown();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                signal.countDown();
            }
        };

        reference.addValueEventListener(listener);
        try {
            return signal.await(timeout, unit) && connected.get();
        } finally {
            reference.removeEventListener(listener);
        }
    }
}
