package com.alidogukan.avora.nas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.alidogukan.avora.R;

import java.util.Locale;
import java.util.UUID;

/** Privacy-preserving identity for one AVORA app installation. */
public final class NasDeviceIdentity {
    private static final String PREFERENCES = "avora_nas_device_identity";
    private static final String INSTALLATION_ID = "installation_id";
    private static final String PREFIX = "android-";

    public final String id;
    public final String name;

    private NasDeviceIdentity(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public static NasDeviceIdentity get(Context context) {
        Context application = context.getApplicationContext();
        SharedPreferences preferences = application.getSharedPreferences(
                PREFERENCES, Context.MODE_PRIVATE);
        String id;
        synchronized (NasDeviceIdentity.class) {
            id = preferences.getString(INSTALLATION_ID, "");
            if (id == null || id.isEmpty()) {
                id = PREFIX + UUID.randomUUID();
                if (!preferences.edit().putString(INSTALLATION_ID, id).commit()) {
                    throw new IllegalStateException("NAS device identity storage failed.");
                }
            }
        }
        return new NasDeviceIdentity(id, deviceName(application));
    }

    private static String deviceName(Context context) {
        String manufacturer = clean(Build.MANUFACTURER);
        String model = clean(Build.MODEL);
        if (model.isEmpty()) {
            return context.getString(R.string.nas_security_android_device);
        }
        if (manufacturer.isEmpty()
                || model.toLowerCase(Locale.ROOT)
                .startsWith(manufacturer.toLowerCase(Locale.ROOT))) {
            return model;
        }
        return titleCase(manufacturer) + " " + model;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String titleCase(String value) {
        if (value.isEmpty()) return value;
        int end = value.offsetByCodePoints(0, 1);
        return value.substring(0, end).toUpperCase(Locale.getDefault())
                + value.substring(end);
    }
}
