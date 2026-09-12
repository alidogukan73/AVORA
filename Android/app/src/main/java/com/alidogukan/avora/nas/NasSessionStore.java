package com.alidogukan.avora.nas;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores only the temporary NAS token, encrypted by the Android Keystore. */
public final class NasSessionStore {
    private static final String PREFERENCES = "avora_nas_session";
    private static final String KEY_ALIAS = "avora_nas_session_v1";
    private static final String CIPHER = "AES/GCM/NoPadding";

    private static final String TOKEN = "token";
    private static final String IV = "iv";
    private static final String EXPIRES_AT = "expires_at";
    private static final String USER_ID = "user_id";
    private static final String EMAIL = "email";
    private static final String DISPLAY_NAME = "display_name";
    private static final String ROLE = "role";

    private final SharedPreferences preferences;

    public NasSessionStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public synchronized void save(NasSession session) {
        if (session == null || !session.isActiveAt(System.currentTimeMillis() / 1000L)) {
            throw new IllegalArgumentException("Active session is required.");
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(
                    session.accessToken.getBytes(StandardCharsets.UTF_8));
            boolean committed = preferences.edit()
                    .putString(TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putLong(EXPIRES_AT, session.expiresAt)
                    .putString(USER_ID, session.user.id)
                    .putString(EMAIL, session.user.email)
                    .putString(DISPLAY_NAME, session.user.displayName)
                    .putString(ROLE, session.user.role)
                    .commit();
            if (!committed) throw new IllegalStateException("Session storage failed.");
        } catch (GeneralSecurityException | IOException error) {
            throw new IllegalStateException("Secure session storage failed.", error);
        }
    }

    public synchronized NasSession load() {
        long expiresAt = preferences.getLong(EXPIRES_AT, 0L);
        if (expiresAt <= System.currentTimeMillis() / 1000L) {
            clear();
            return null;
        }
        String encrypted = preferences.getString(TOKEN, "");
        String iv = preferences.getString(IV, "");
        if (encrypted == null || encrypted.isEmpty() || iv == null || iv.isEmpty()) {
            clear();
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            String token = new String(
                    cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
            NasSession.User user = new NasSession.User(
                    preferences.getString(USER_ID, ""),
                    preferences.getString(EMAIL, ""),
                    preferences.getString(DISPLAY_NAME, ""),
                    preferences.getString(ROLE, ""));
            return new NasSession(token, expiresAt, user);
        } catch (Exception error) {
            clear();
            return null;
        }
    }

    public synchronized void clear() {
        preferences.edit().clear().commit();
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
