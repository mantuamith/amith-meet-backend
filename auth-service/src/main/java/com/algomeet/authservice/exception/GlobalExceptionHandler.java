package com.algomeet.authservice.exception;

import com.algomeet.authservice.dto.ErrorBody;
import com.algomeet.authservice.enums.ResponseCode;
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

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<?> handleUserAlreadyExists(UserAlreadyExistsException ex, HttpServletRequest req) {
        Set<String> fields = ex.getFields() == null ? Set.of() : ex.getFields();
        ResponseCode code = selectDuplicateCode(fields);

        log.warn("409 UserAlreadyExists fields={} path={} method={}", fields, req.getRequestURI(), req.getMethod());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "UserAlreadyExists",
                "code", code.getCode(),
                "message", code.getDefaultMessage(),
                "fields", fields,
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
    }

    // -------- Framework-level overrides (avoid ambiguity with base class) --------

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        HttpStatus httpStatus = HttpStatus.BAD_REQUEST;
        String path = servletPath(request);
        String method = httpMethod(request);

        log.warn("{} {} Malformed JSON path={} method={} msg={}",
                httpStatus.value(), httpStatus.getReasonPhrase(), path, method, ex.getMostSpecificCause().getMessage());

        ErrorBody body = ErrorBody.builder()
                .status(httpStatus.value())
                .error(httpStatus.getReasonPhrase())
                .message("Malformed JSON request")
                .path(path)
                .method(method)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.status(httpStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

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

    // -------- Your app/domain handlers (unchanged semantics) --------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        Set<String> fields = new HashSet<>();
        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException cve) {
            String constraint = cve.getConstraintName();
            if (constraint != null) {
                if (constraint.contains("uk_users_email")) fields.add("email");
                if (constraint.contains("uk_users_username")) fields.add("username");
            }
        }
        ResponseCode code = selectDuplicateCode(fields.isEmpty() ? Set.of("unknown") : fields);

        log.warn("409 DataIntegrityViolation fields={} constraintCause={} path={} method={}",
                fields, cause != null ? cause.getClass().getSimpleName() : "null",
                req.getRequestURI(), req.getMethod());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "UserAlreadyExists",
                "code", code.getCode(),
                "message", code.getDefaultMessage(),
                "fields", fields.isEmpty() ? List.of("unknown") : fields,
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
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

    @ExceptionHandler({
            OtpNotFoundException.class,
            OtpInvalidCodeException.class,
    })
    public ResponseEntity<Map<String, Object>> handleOtpInvalid(RuntimeException ex, HttpServletRequest req) {
        log.warn("401 OTP invalid path={} method={} msg={}", req.getRequestURI(), req.getMethod(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "status", 401,
                "error", "Unauthorized",
                "code", ResponseCode.AUTH_FORGOT_INVALID_OTP.getCode(),
                "message", ResponseCode.AUTH_FORGOT_INVALID_OTP.getDefaultMessage(),
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleOtpExpired(OtpExpiredException ex, HttpServletRequest req) {
        log.warn("401 OTP expired path={} method={}", req.getRequestURI(), req.getMethod());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "status", 401,
                "error", "Unauthorized",
                "code", ResponseCode.AUTH_FORGOT_EXPIRED_OTP.getCode(),
                "message", ResponseCode.AUTH_FORGOT_EXPIRED_OTP.getDefaultMessage(),
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(OtpAttemptsExceededException.class)
    public ResponseEntity<Map<String, Object>> handleOtpAttempts(OtpAttemptsExceededException ex, HttpServletRequest req) {
        log.warn("429 OTP attempts exceeded path={} method={}", req.getRequestURI(), req.getMethod());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "code", ResponseCode.AUTH_FORGOT_ATTEMPTS_EXCEEDED.getCode(),
                "message", ResponseCode.AUTH_FORGOT_ATTEMPTS_EXCEEDED.getDefaultMessage(),
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(OtpChannelMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleOtpChannelMismatch(OtpChannelMismatchException ex, HttpServletRequest req) {
        log.warn("400 OTP channel mismatch path={} method={}", req.getRequestURI(), req.getMethod());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", 400,
                "error", "Bad Request",
                "code", ResponseCode.AUTH_FORGOT_CHANNEL_MISMATCH.getCode(),
                "message", ResponseCode.AUTH_FORGOT_CHANNEL_MISMATCH.getDefaultMessage(),
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ResetTicketInvalidException.class)
    public ResponseEntity<Map<String, Object>> handleResetTicketInvalid(
            ResetTicketInvalidException ex, HttpServletRequest req) {

        log.warn("400 ResetTicketInvalid path={} method={} msg={}",
                req.getRequestURI(), req.getMethod(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", 400,
                "error", "Bad Request",
                "code", ResponseCode.AUTH_FORGOT_INVALID_TICKET.getCode(),
                "message", ResponseCode.AUTH_FORGOT_INVALID_TICKET.getDefaultMessage(),
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(ResetTicketExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleResetTicketExpired(
            ResetTicketExpiredException ex, HttpServletRequest req) {

        log.warn("410 Reset Ticket Expired path={} method={} msg={}",
                req.getRequestURI(), req.getMethod(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "status", 410,
                "error", "Gone",
                "code", ResponseCode.AUTH_FORGOT_EXPIRED_TICKET.getCode(),
                "message", ResponseCode.AUTH_FORGOT_EXPIRED_TICKET.getDefaultMessage(),
                "path", req.getRequestURI(),
                "method", req.getMethod(),
                "timestamp", Instant.now().toString()
        ));
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

    private ResponseCode selectDuplicateCode(Set<String> fields) {
        boolean email = fields.contains("email");
        boolean username = fields.contains("username");
        if (email && username) return ResponseCode.AUTH_DUPLICATE_BOTH;
        if (email) return ResponseCode.AUTH_DUPLICATE_EMAIL;
        if (username) return ResponseCode.AUTH_DUPLICATE_USERNAME;
        return ResponseCode.AUTH_DUPLICATE_REGISTER_REQUEST;
    }
}
