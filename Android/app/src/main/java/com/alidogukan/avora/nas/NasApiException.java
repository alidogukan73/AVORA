package com.alidogukan.avora.nas;

/** Stable internal error code for NAS connectivity and protocol failures. */
public final class NasApiException extends Exception {
    public NasApiException(String code) {
        super(code);
    }

    public NasApiException(String code, Throwable cause) {
        super(code, cause);
    }
}
