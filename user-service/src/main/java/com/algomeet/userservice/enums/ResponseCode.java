package com.algomeet.userservice.enums;

public enum ResponseCode {
    AUTH_REGISTER_SUCCESS("AUTH_REGISTER_SUCCESS", "User registered successfully"),
    AUTH_DUPLICATE_REGISTER_REQUEST("USER_DUPLICATE_EMAIL", "User with this email already exists.");

    private final String code;
    private final String defaultMessage;

    ResponseCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
