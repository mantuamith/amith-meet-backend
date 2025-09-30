// src/main/java/com/algomeet/contactservice/web/GlobalExceptionHandler.java
package com.algomeet.contactservice.web;

import com.algomeet.contactservice.enums.ResponseCode;
import com.algomeet.contactservice.i18n.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageResolver i18n;

    public GlobalExceptionHandler(MessageResolver i18n) {
        this.i18n = i18n;
    }

    // --- 401 / 403 ---

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ErrorBody> handleInsufficientAuth(InsufficientAuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ResponseCode.AUTH_SESSION_REVOKED, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorBody> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ResponseCode.ACCESS_DENIED, req);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorBody> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        ResponseCode rc = mapStatusToCode(ex.getStatusCode());
        String msg = ex.getReason() != null ? ex.getReason() : i18n.msg(rc);
        return build(ex.getStatusCode(), rc, msg, req);
    }

    // --- 400s ---

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + (fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, details, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorBody> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        String details = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, details, req);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorBody> handleBadInput(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, friendly(ex), req);
    }

    // --- 405 / 415 ---

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ResponseCode.METHOD_NOT_ALLOWED, req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorBody> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ResponseCode.UNSUPPORTED_MEDIA_TYPE, req);
    }

    // --- Domain / generic ---

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleIllegalArg(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, nonBlankOr(ex.getMessage(), i18n.msg(ResponseCode.BAD_REQUEST)), req);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorBody> handleNotFound(NoSuchElementException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ResponseCode.NOT_FOUND, nonBlankOr(ex.getMessage(), i18n.msg(ResponseCode.NOT_FOUND)), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleAny(Exception ex, HttpServletRequest req) {
        log.error("500 at {} {}: {}", req.getMethod(), req.getRequestURI(), ex.toString(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ResponseCode.INTERNAL_ERROR, req);
    }

    // --- helpers ---

    private ResponseEntity<ErrorBody> build(HttpStatus status, ResponseCode rc, HttpServletRequest req) {
        return build(status, rc, i18n.msg(rc), req);
    }

    private ResponseEntity<ErrorBody> build(HttpStatusCode status, ResponseCode rc, String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new ErrorBody(rc.getCode(), message, req.getRequestURI(), Instant.now().toString()));
    }

    private static ResponseCode mapStatusToCode(HttpStatusCode status) {
        if (status == HttpStatus.UNAUTHORIZED) return ResponseCode.AUTH_SESSION_REVOKED;
        if (status == HttpStatus.FORBIDDEN)    return ResponseCode.ACCESS_DENIED;
        if (status == HttpStatus.NOT_FOUND)    return ResponseCode.NOT_FOUND;
        if (status == HttpStatus.BAD_REQUEST)  return ResponseCode.BAD_REQUEST;
        if (status == HttpStatus.METHOD_NOT_ALLOWED) return ResponseCode.METHOD_NOT_ALLOWED;
        if (status == HttpStatus.UNSUPPORTED_MEDIA_TYPE) return ResponseCode.UNSUPPORTED_MEDIA_TYPE;
        return ResponseCode.INTERNAL_ERROR;
    }

    private static String friendly(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) return "Invalid request";
        return msg.replaceAll("\\s*;.*$", "");
    }

    private static String nonBlankOr(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    public record ErrorBody(String code, String message, String path, String timestamp) {}
}
