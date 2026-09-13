package com.alidogukan.avora.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import com.alidogukan.avora.R;
import com.alidogukan.avora.nas.NasApiException;
import com.alidogukan.avora.nas.NasAuthClient;
import com.alidogukan.avora.nas.NasSecurityRepository;
import com.alidogukan.avora.nas.NasSession;

import java.util.List;

/** Daily administrator-only review of family members inactive for 30 days. */
public final class NasInactiveAccessMonitor {
    private static final String PREFERENCES = "avora_nas_inactive_access";
    private static final String NEXT_CHECK_AT = "next_check_at";
    private static final long SUCCESS_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final long RETRY_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private NasInactiveAccessMonitor() { }

    public static void check(Context context) {
        Context app = context.getApplicationContext();
        NasSecurityRepository repository = new NasSecurityRepository(app);
        NasSession session = repository.loadSession();
        if (session == null || !"admin".equals(session.user.role)) return;

        SharedPreferences preferences = app.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now < preferences.getLong(NEXT_CHECK_AT, 0L)) return;
        preferences.edit().putLong(NEXT_CHECK_AT, now + RETRY_INTERVAL_MS).apply();

        try {
            List<NasAuthClient.AccountSummary> accounts = repository.accounts(session);
            GardenNotificationManager notifications =
                    new GardenNotificationManager(app);
            for (NasAuthClient.AccountSummary account : accounts) {
                if (!account.inactiveAccess || !"user".equals(account.role)) continue;
                long reviewAnchor = Math.max(
                        account.lastActiveAt, account.inactivityReviewedAt);
                notifications.publishOnce(
                        "ACCESS",
                        "HIGH",
                        "",
                        app.getString(R.string.notification_inactive_access_title),
                        app.getString(R.string.notification_inactive_access_description,
                                account.displayName),
                        "inactive-access:" + account.id + ":" + reviewAnchor);
            }
            preferences.edit()
                    .putLong(NEXT_CHECK_AT, now + SUCCESS_INTERVAL_MS)
                    .apply();
        } catch (Exception error) {
            if (isExpired(error)) repository.expireLocalSession();
        }
    }

    private static boolean isExpired(Exception error) {
        return error instanceof NasApiException
                && "NAS_SESSION_EXPIRED".equals(error.getMessage());
    }
}
