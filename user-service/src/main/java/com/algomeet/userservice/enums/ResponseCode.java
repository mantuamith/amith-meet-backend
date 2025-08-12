package com.algomeet.userservice.enums;

public enum ResponseCode {
    AUTH_REGISTER_SUCCESS("AUTH_REGISTER_SUCCESS", "User registered successfully"),
    AUTH_DUPLICATE_BOTH("AUTH_DUPLICATE_BOTH", "User and email both already exists."),
    AUTH_DUPLICATE_EMAIL("AUTH_DUPLICATE_EMAIL","Email already exists."),
    AUTH_DUPLICATE_USERNAME("AUTH_DUPLICATE_USERNAME","User name already exists")
    ;



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
