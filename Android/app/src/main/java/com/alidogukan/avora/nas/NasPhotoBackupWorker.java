package com.alidogukan.avora.nas;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Incrementally uploads private local photos while the app is closed. */
public final class NasPhotoBackupWorker extends Worker {
    public NasPhotoBackupWorker(@NonNull Context context,
                                @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        NasPhotoBackupSettings settings = new NasPhotoBackupSettings(context);
        if (!settings.isEnabled()) return Result.success();

        NasSessionStore sessionStore = new NasSessionStore(context);
        NasSession session = sessionStore.load();
        if (session == null) {
            return failure(settings, "NAS_SESSION_EXPIRED", false);
        }
        try {
            NasPhotoBackupManager.BackupResult result =
                    new NasPhotoBackupManager(context).backup(session.accessToken);
            settings.recordSuccess(System.currentTimeMillis(), result.remoteCount);
            new NasBackupFailureNotifier(context).cancelPhoto();
            return Result.success();
        } catch (Exception error) {
            String code = errorCode(error);
            if ("NAS_SESSION_EXPIRED".equals(code)) {
                sessionStore.clear();
                return failure(settings, code, false);
            }
            boolean retry = "NAS_TIMEOUT".equals(code)
                    || "NAS_UNAVAILABLE".equals(code);
            return failure(settings, code, retry);
        }
    }

    private Result failure(NasPhotoBackupSettings settings, String code,
                           boolean retry) {
        if (settings.recordFailureAndShouldNotify(code)) {
            new NasBackupFailureNotifier(getApplicationContext()).showPhoto(code);
        }
        return retry ? Result.retry() : Result.success();
    }

    static String errorCode(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.ExecutionException
                || current instanceof java.util.concurrent.CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? "" : current.getMessage();
        if (message != null && message.startsWith("NAS_")) return message;
        return "NAS_PHOTO_BACKUP_FAILED";
    }
}
