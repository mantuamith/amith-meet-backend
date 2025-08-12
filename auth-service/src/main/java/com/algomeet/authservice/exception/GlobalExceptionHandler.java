package com.algomeet.authservice.exception;

import com.algomeet.authservice.enums.ResponseCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.*;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<?> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        Set<String> fields = ex.getFields() == null ? Set.of() : ex.getFields();

        ResponseCode code = selectDuplicateCode(fields);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "UserAlreadyExists",
                "code", code.getCode(),
                "message", code.getDefaultMessage(),
                "fields", fields
        ));
    }

    // Safety net for DB-level unique constraint violations (race conditions, etc.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException ex) {
        Set<String> fields = new HashSet<>();

        Throwable cause = ex.getCause();
        if (cause instanceof ConstraintViolationException cve) {
            String constraint = cve.getConstraintName();
            if (constraint != null) {
                // Match your @UniqueConstraint names from the User entity
                if (constraint.contains("uk_users_email")) fields.add("email");
                if (constraint.contains("uk_users_username")) fields.add("username");
            }
        }

        ResponseCode code = selectDuplicateCode(fields.isEmpty() ? Set.of("unknown") : fields);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "UserAlreadyExists",
                "code", code.getCode(),
                "message", code.getDefaultMessage(),
                "fields", fields.isEmpty() ? List.of("unknown") : fields
        ));
    }

    private ResponseCode selectDuplicateCode(Set<String> fields) {
        boolean email = fields.contains("email");
        boolean username = fields.contains("username");

        if (email && username) return ResponseCode.AUTH_DUPLICATE_BOTH;
        if (email)           return ResponseCode.AUTH_DUPLICATE_EMAIL;
        if (username)        return ResponseCode.AUTH_DUPLICATE_USERNAME;

        // Fallback (shouldn’t normally hit)
        return ResponseCode.AUTH_DUPLICATE_REGISTER_REQUEST;
    }
}
