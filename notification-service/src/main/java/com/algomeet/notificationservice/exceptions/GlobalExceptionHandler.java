package com.algomeet.notificationservice.exceptions;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.algomeet.notificationservice.util.MessageUtil;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleInputValidationException(MethodArgumentNotValidException ex) {
    	Map<String, String> errors = new HashMap<>();
    	
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
    	
    	return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "InputValidationError",
                "code", "INPUT_VALIDATION_ERROR",
                "message", MessageUtil.getMessage("input.validation.error"),
                "fields", errors
        ));
    }
    
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<?> handleFeignxception(FeignException ex) {   
    	log.error("{}", ex.getMessage(), ex);
    	return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "FeignError",
                "code", "FEIGN_ERROR",
                "message", MessageUtil.getMessage("service.error")
        ));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleServiceException (Exception ex) {  
    	log.error("{}", ex.getMessage(), ex);
    	return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "ServiceError",
                "code", "SERVICE_ERROR",
                "message", MessageUtil.getMessage("service.error")
        ));
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimexception (RuntimeException ex) {  
    	log.error("{}", ex.getMessage(), ex);
    	return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "ServiceError",
                "code", "SERVICE_ERROR",
                "message", MessageUtil.getMessage("service.error")
        ));
    }
}
