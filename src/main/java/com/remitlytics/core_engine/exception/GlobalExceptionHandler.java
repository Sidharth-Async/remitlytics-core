package com.remitlytics.core_engine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles state machine guard failures (e.g., PAID -> DRAFT)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.CONFLICT.value(),
                "error", "ILLEGAL_STATE_TRANSITION",
                "message", ex.getMessage()
        ));
    }

    // Handles JPA @PreUpdate immutability listener violations
    @ExceptionHandler(ReadOnlyLedgerException.class)
    public ResponseEntity<Map<String, Object>> handleReadOnlyLedger(ReadOnlyLedgerException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.LOCKED.value(),
                "error", "LOCKED_RECORD",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException ex) {
        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "NOT_FOUND",
                "message", ex.getMessage()
        );
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }
}