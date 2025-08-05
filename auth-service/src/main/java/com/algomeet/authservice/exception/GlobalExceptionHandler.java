package com.algomeet.authservice.exception;

import com.algomeet.authservice.enums.ResponseCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<?> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "UserAlreadyExists",
                "code", ResponseCode.AUTH_DUPLICATE_REGISTER_REQUEST.getCode(),
                "message", ResponseCode.AUTH_DUPLICATE_REGISTER_REQUEST.getDefaultMessage()
        ));
    }

    // (You can add more handlers here later)
}
