package com.algomeet.authservice.exception;

import lombok.Getter;

import java.util.Set;

@Getter
public class UserAlreadyExistsException extends RuntimeException {
    private final Set<String> fields; // e.g., ["email"], ["username"], ["email","username"]

    public UserAlreadyExistsException(Set<String> fields) {
        super("Duplicate fields: " + String.join(", ", fields));
        this.fields = fields;
    }

}
