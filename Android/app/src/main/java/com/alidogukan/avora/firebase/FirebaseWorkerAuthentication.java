package com.alidogukan.avora.firebase;

import com.google.android.gms.tasks.Tasks;

import java.util.concurrent.TimeUnit;

/** Shared authentication gate for Firebase-backed WorkManager jobs. */
public final class FirebaseWorkerAuthentication {
    private FirebaseWorkerAuthentication() { }

    public static boolean awaitAuthorized(long timeout, TimeUnit unit)
            throws Exception {
        return Boolean.TRUE.equals(Tasks.await(
                new FirebaseRepository().authenticateAnonymously(),
                timeout,
                unit
        ));
    }
}
