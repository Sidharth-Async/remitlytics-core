package com.remitlytics.core_engine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "NOT_FOUND",
                "message", ex.getMessage()
        ));
    }

    // Handles missing headers like X-Tenant-ID
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "MISSING_REQUIRED_HEADER",
                "message", String.format("Required header '%s' is missing", ex.getHeaderName())
        ));
    }

    // Handles malformed UUIDs passed in URL path or headers
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "INVALID_PARAMETER_FORMAT",
                "message", String.format("Parameter '%s' has an invalid format or value: '%s'", ex.getName(), ex.getValue())
        ));
    }
}