package com.alidogukan.avora.fertilization;

import static org.junit.Assert.assertEquals;

import com.alidogukan.avora.R;
import com.alidogukan.avora.firebase.DatabaseWriteException;
import com.google.firebase.database.DatabaseError;
import org.junit.Test;

public final class FertilizationSaveErrorTest {
    @Test public void permissionFailureHasAnActionableMessage() {
        assertEquals(R.string.fertilization_save_access_failed,
                message(DatabaseError.PERMISSION_DENIED));
    }
    @Test public void networkFailuresHaveAConnectionMessage() {
        for (int code : new int[]{DatabaseError.DISCONNECTED, DatabaseError.NETWORK_ERROR,
                DatabaseError.UNAVAILABLE}) {
            assertEquals(R.string.fertilization_save_connection_failed, message(code));
        }
    }
    @Test public void exhaustedTransactionRetriesHaveARetryMessage() {
        assertEquals(R.string.fertilization_save_busy, message(DatabaseError.MAX_RETRIES));
    }
    @Test public void stockAndSeasonFailuresRetainTheirMeaning() {
        assertEquals(R.string.fertilization_insufficient_stock,
                FertilizationSaveError.messageResource(new IllegalStateException(
                        "Gübre stoğu bu uygulama için yetersiz.")));
        assertEquals(R.string.fertilization_save_season_closed,
                FertilizationSaveError.messageResource(new IllegalStateException(
                        "Bu bölgenin sezonu kapalı. Önce yeni sezon başlatın.")));
    }
    @Test public void unexpectedDetailsAreNeverShown() {
        assertEquals(R.string.fertilization_application_failed,
                FertilizationSaveError.messageResource(new IllegalStateException(
                        "Internal private backend detail")));
        assertEquals(R.string.fertilization_application_failed,
                message(DatabaseError.UNKNOWN_ERROR));
    }
    private int message(int code) {
        return FertilizationSaveError.messageResource(
                new DatabaseWriteException(DatabaseError.fromCode(code)));
    }
}
