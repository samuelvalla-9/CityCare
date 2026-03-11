package com.citycare.exception;

import com.citycare.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================
 * GlobalExceptionHandler.java  –  Converts exceptions to JSON
 * ============================================================
 *
 * Without this, Spring would return HTML error pages or raw
 * stack traces to the client. This converts every exception
 * into a clean ApiResponse JSON object.
 *
 * @RestControllerAdvice – intercepts exceptions from ALL controllers.
 *
 * EXCEPTION → HTTP STATUS MAPPING:
 *
 *   ResourceNotFoundException         → 404 Not Found
 *   BadRequestException               → 400 Bad Request
 *   MethodArgumentNotValidException   → 400 Bad Request (with field errors)
 *   BadCredentialsException           → 401 Unauthorized
 *   AccessDeniedException             → 403 Forbidden
 *   Exception (catch-all)             → 500 Internal Server Error
 *
 * ============================================================
 * HOW THIS FILE WORKS:
 *   Services throw exceptions (e.g. throw new BadRequestException("..."))
 *   Spring catches them here and returns a proper JSON error response.
 *   Controllers don't need try/catch blocks anywhere.
 * ============================================================
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Handles @Valid failures – returns per-field error messages
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = ((FieldError) err).getField();
            errors.put(field, err.getDefaultMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed – check field errors")
                        .data(errors)
                        .build());
    }

    // Wrong email or password during login
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    // User authenticated but doesn't have the required role
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied – your role cannot perform this action"));
    }

    // Catch-all for unexpected server errors
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Unexpected error: " + ex.getMessage()));
    }
}
