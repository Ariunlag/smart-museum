package com.smartmuseum.core.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // BleIngestRequest validation алдаа → 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> onValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error",   "validation_failed",
                "message", ex.getMessage()
        ));
    }

    // Positioning / ArtInfo service алдаа → 502
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<?> onUpstream(WebClientResponseException ex) {
        return ResponseEntity.status(502).body(Map.of(
                "error",   "upstream_error",
                "message", ex.getMessage()
        ));
    }

    // Бусад алдаа → 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> onAny(Exception ex) {
        return ResponseEntity.internalServerError().body(Map.of(
                "error",   "internal_error",
                "message", ex.getMessage()
        ));
    }
}