package com.alidogukan.avora.nas;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.alidogukan.avora.backup.AvoraBackupManager;
import com.alidogukan.avora.config.AppInfo;
import com.alidogukan.avora.firebase.FirebaseWorkerAuthentication;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Creates the daily Firebase-to-NAS recovery point while the app is closed. */
public final class NasAutomaticBackupWorker extends Worker {
    public NasAutomaticBackupWorker(@NonNull Context context,
                                    @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        NasAutomaticBackupSettings settings = new NasAutomaticBackupSettings(context);
        if (!settings.isEnabled()) return Result.success();

        NasSessionStore sessionStore = new NasSessionStore(context);
        NasSession session = sessionStore.load();
        if (session == null) {
            return failure(settings, "NAS_SESSION_EXPIRED", false);
        }
        try {
            if (!FirebaseWorkerAuthentication.awaitAuthorized(25, TimeUnit.SECONDS)) {
                return failure(settings, "FIREBASE_NOT_AUTHORIZED", false);
            }
            JSONObject backup = Tasks.await(
                    new AvoraBackupManager(context).createBackup(),
                    40, TimeUnit.SECONDS);
            NasBackupArchive.save(session.accessToken, AppInfo.DEVICE_ID, backup);
            settings.recordSuccess(System.currentTimeMillis());
            new NasBackupFailureNotifier(context).cancel();
            return Result.success();
        } catch (Exception error) {
            String code = errorCode(error);
            if ("NAS_SESSION_EXPIRED".equals(code)) {
                sessionStore.clear();
                return failure(settings, code, false);
            }
            boolean retry = "NAS_TIMEOUT".equals(code)
                    || "NAS_UNAVAILABLE".equals(code)
                    || error instanceof java.util.concurrent.TimeoutException;
            return failure(settings, code, retry);
        }
    }

    private Result failure(NasAutomaticBackupSettings settings, String code,
                           boolean retry) {
        if (settings.recordFailureAndShouldNotify(code)) {
            new NasBackupFailureNotifier(getApplicationContext()).show(code);
        }
        return retry ? Result.retry() : Result.success();
    }

    static String errorCode(Throwable error) {
        Throwable current = error;
        while ((current instanceof ExecutionException
                || current instanceof java.util.concurrent.CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? "" : current.getMessage();
        if (message == null || message.isBlank()) return "NAS_BACKUP_FAILED";
        if (message.startsWith("NAS_")) return message;
        if (current instanceof SecurityException) return "FIREBASE_NOT_AUTHORIZED";
        return "NAS_BACKUP_FAILED";
    }
}
