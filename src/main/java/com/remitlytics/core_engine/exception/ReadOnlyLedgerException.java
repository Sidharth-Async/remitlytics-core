package com.remitlytics.core_engine.exception;

public class ReadOnlyLedgerException extends RuntimeException {
    public ReadOnlyLedgerException(String message) {
        super(message);
    }
}
