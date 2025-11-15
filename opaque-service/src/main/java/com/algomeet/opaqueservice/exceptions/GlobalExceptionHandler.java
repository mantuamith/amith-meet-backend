package com.algomeet.opaqueservice.exceptions;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // Handle validation errors (e.g. @NotNull, @NotBlank)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage())
        );

        log.warn("400 ValidationError fields={} path={} method={}", errors.keySet(), req.getRequestURI(), req.getMethod());

        return ResponseEntity.badRequest().body(Map.of(
                "error", "ValidationError",
                "message", "Invalid request",
                "fields", errors,
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Handle invalid enum values (e.g. deviceType="TABLET")
    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<?> handleInvalidFormat(InvalidFormatException ex, HttpServletRequest req) {
        Map<String, String> error = new HashMap<>();
        if (ex.getTargetType() != null && ex.getTargetType().isEnum()) {
            String field = ex.getPath().isEmpty() ? "unknown" : ex.getPath().get(0).getFieldName();
            Class<?> enumClass = ex.getTargetType();
            Object[] constants = enumClass.getEnumConstants();
            error.put(field, "must be one of " + Arrays.toString(constants));

            log.warn("400 InvalidEnum field={} allowed={} path={} method={}",
                    field, Arrays.toString(constants), req.getRequestURI(), req.getMethod());

            return ResponseEntity.badRequest().body(Map.of(
                    "error", "ValidationError",
                    "message", "Invalid enum value",
                    "fields", error,
                    "path", req.getRequestURI(),
                    "method", req.getMethod(),
                    "timestamp", Instant.now().toString()
            ));
        }

        log.warn("400 InvalidFormat value={} path={} method={}", ex.getValue(), req.getRequestURI(), req.getMethod());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "InvalidFormat",
                "message", "Invalid value: " + ex.getValue(),
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
    }

    // Handle ResponseStatusException (e.g., single-device or policy blocks)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String reason = ex.getReason() == null ? status.getReasonPhrase() : ex.getReason();

        if (status != null && status.is4xxClientError()) {
            log.warn("{} {} path={} method={} reason={}",
                    ex.getStatusCode().value(), status.getReasonPhrase(), req.getRequestURI(), req.getMethod(), reason);
        } else {
            log.error("{} {} path={} method={} reason={}",
                    ex.getStatusCode().value(), status != null ? status.getReasonPhrase() : "Unknown", req.getRequestURI(), req.getMethod(), reason, ex);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("error", status != null ? status.getReasonPhrase() : ex.getStatusCode().toString());
        body.put("message", reason);
        body.put("path", req.getRequestURI());
        body.put("method", req.getMethod());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    // Feign client errors (e.g., user-service 404)
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeign(FeignException ex, HttpServletRequest req) {
        int status = ex.status() == -1 ? 502 : ex.status();
        if (status >= 500) {
            log.error("Feign {} downstreamError path={} method={} msg={}", status, req.getRequestURI(), req.getMethod(), ex.getMessage(), ex);
        } else {
            log.warn("Feign {} downstreamClientError path={} method={} msg={}", status, req.getRequestURI(), req.getMethod(), ex.getMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", HttpStatus.resolve(status) != null ? HttpStatus.resolve(status).getReasonPhrase() : "Upstream Error");
        body.put("message", "Upstream call failed");
        body.put("path", req.getRequestURI());
        body.put("method", req.getMethod());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    // Catch-all to avoid HTML error pages leaking out
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("500 Unexpected error path={} method={} err={}", req.getRequestURI(), req.getMethod(), ex.toString(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 500);
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred");
        body.put("path", req.getRequestURI());
        body.put("method", req.getMethod());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
    
 // Catch-all to avoid HTML error pages leaking out
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(Exception ex, HttpServletRequest req) {
        log.error("403 Access Denied error path={} method={} err={}", req.getRequestURI(), req.getMethod(), ex.toString(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 403);
        body.put("error", "403 Access Denied");
        body.put("message", "Access Denied");
        body.put("path", req.getRequestURI());
        body.put("method", req.getMethod());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}
