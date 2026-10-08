package com.alidogukan.avora.fertilization;

import com.alidogukan.avora.R;
import com.alidogukan.avora.firebase.DatabaseWriteException;
import com.google.firebase.database.DatabaseError;

/** Only known, actionable messages are displayed; unknown backend text stays private. */
public final class FertilizationSaveError {
    private FertilizationSaveError() { }

    public static int messageResource(Exception error) {
        if (error instanceof DatabaseWriteException) {
            switch (((DatabaseWriteException) error).getCode()) {
                case DatabaseError.PERMISSION_DENIED:
                case DatabaseError.EXPIRED_TOKEN:
                case DatabaseError.INVALID_TOKEN:
                    return R.string.fertilization_save_access_failed;
                case DatabaseError.DISCONNECTED:
                case DatabaseError.NETWORK_ERROR:
                case DatabaseError.UNAVAILABLE:
                    return R.string.fertilization_save_connection_failed;
                case DatabaseError.MAX_RETRIES:
                    return R.string.fertilization_save_busy;
                default: break;
            }
        }
        if (error instanceof IllegalStateException) {
            String message = error.getMessage();
            if ("Gübre stoğu bu uygulama için yetersiz.".equals(message)) {
                return R.string.fertilization_insufficient_stock;
            }
            if ("Bu bölgenin sezonu kapalı. Önce yeni sezon başlatın.".equals(message)) {
                return R.string.fertilization_save_season_closed;
            }
            if ("Gübre stok birimi uygulama birimiyle uyuşmuyor.".equals(message)) {
                return R.string.fertilization_save_unit_mismatch;
            }
        }
        return R.string.fertilization_application_failed;
    }
}
