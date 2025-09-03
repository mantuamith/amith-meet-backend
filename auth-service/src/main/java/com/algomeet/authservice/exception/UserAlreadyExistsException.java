package com.algomeet.authservice.exception;

import com.algomeet.authservice.enums.ResponseCode;
import lombok.Getter;

import java.util.Set;

@Getter
public class UserAlreadyExistsException extends RuntimeException {
    @Getter
    private final Set<String> fields; // e.g., ["email"], ["username"], ["email","username"]
    private final ResponseCode code;

    public UserAlreadyExistsException(String message, Set<String> fields, ResponseCode code) {
        super(message);
        this.fields = fields;
        this.code = code;
    }

}
