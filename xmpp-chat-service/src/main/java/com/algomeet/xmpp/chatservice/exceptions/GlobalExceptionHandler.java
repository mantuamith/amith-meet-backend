package com.algomeet.xmpp.chatservice.exceptions;

import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            // last message wins per field like your prior impl
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        String path = servletPath(request);
        String method = httpMethod(request);

        log.warn("400 ValidationError fields={} path={} method={}", fieldErrors.keySet(), path, method);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "ValidationError");
        body.put("message", "Invalid request");
        body.put("fields", fieldErrors);
        body.put("path", path);
        body.put("method", method);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(body);
    }

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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String reason = ex.getReason() == null ? (status != null ? status.getReasonPhrase() : ex.getStatusCode().toString()) : ex.getReason();

        if (status != null && status.is4xxClientError()) {
            log.warn("{} {} path={} method={} reason={}",
                    ex.getStatusCode().value(), status.getReasonPhrase(), req.getRequestURI(), req.getMethod(), reason);
        } else {
            log.error("{} {} path={} method={} reason={}",
                    ex.getStatusCode().value(), status != null ? status.getReasonPhrase() : "Unknown",
                    req.getRequestURI(), req.getMethod(), reason, ex);
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

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeign(FeignException ex, HttpServletRequest req) {
        int status = (ex.status() == -1) ? 502 : ex.status();

        if (status >= 500) {
            // concise
            log.error("Feign {} downstreamError path={} method={} msg={} (enable DEBUG for stacktrace)",
                    status, req.getRequestURI(), req.getMethod(), ex.getMessage());
            // detailed only at DEBUG
            if (log.isDebugEnabled()) log.debug("Feign exception stacktrace", ex);
        } else {
            log.warn("Feign {} downstreamClientError path={} method={} msg={}",
                    status, req.getRequestURI(), req.getMethod(), ex.getMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", HttpStatus.resolve(status) != null
                ? HttpStatus.resolve(status).getReasonPhrase()
                : "Upstream Error");
        body.put("message", "Upstream call failed");
        body.put("path", req.getRequestURI());
        body.put("method", req.getMethod());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest req) {
        // concise one-liner at ERROR (no stack trace)
        log.error("500 Unexpected error path={} method={} err={} (enable DEBUG for stacktrace)",
                req.getRequestURI(), req.getMethod(), ex.toString());

        // full stack trace only when DEBUG is on
        if (log.isDebugEnabled()) {
            log.debug("Unhandled exception stacktrace", ex);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 500);
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred");
        body.put("path", req.getRequestURI());
        body.put("method", req.getMethod());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("403 AccessDenied path={} method={} msg={}", req.getRequestURI(), req.getMethod(), ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 403);
        body.put("error", "Access Denied");
        body.put("message", "Forbidden access");
        body.put("path", req.getRequestURI());
        body.put("method", req.getMethod());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // --- helpers ---

    private String servletPath(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return "";
    }

    private String httpMethod(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getMethod();
        }
        return "";
    }
}
