package com.alidogukan.avora.firebase;

import com.google.firebase.database.DatabaseError;

/** Carries a structured failure code without exposing backend details in the UI. */
public final class DatabaseWriteException extends Exception {
    private final int code;

    public DatabaseWriteException(DatabaseError error) {
        super("Database write failed", error.toException());
        code = error.getCode();
    }

    public int getCode() { return code; }
}
