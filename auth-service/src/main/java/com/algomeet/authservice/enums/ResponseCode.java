package com.algomeet.authservice.enums;

public enum ResponseCode {
    AUTH_REGISTER_SUCCESS("AUTH_REGISTER_SUCCESS", "User registered successfully"),
    AUTH_DUPLICATE_REGISTER_REQUEST("AUTH_DUPLICATE_EMAIL", "User with this email already exists."),
    AUTH_LOGIN_SUCCESS("AUTH_LOGIN_SUCCESS", "Login Successful"),
    AUTH_REFRESH_SUCCESS("AUTH_REFRESH_SUCCESS", "Access token refreshed successfully"),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid credentials"),
    AUTH_LOGIN_FAILED("AUTH_LOGIN_FAILED", "Unexpected error during login"),
    AUTH_USER_DELETED("AUTH_USER_DELETED", "User Account De-activated Successfully"),
    AUTH_DELETE_FAILED("AUTH_DELETE_FAILED", "Account Deletion Failed"),
    AUTH_INVALID_REFRESH_TOKEN("AUTH_INVALID_REFRESH_TOKEN", "Invalid or expired refresh token"),
    AUTH_REFRESH_FAILED("AUTH_REFRESH_FAILED", "Unexpected error during token refresh"),
    AUTH_LOGOUT_SUCCESS("AUTH_LOGOUT_SUCCESS", "User logged out successfully"),
    AUTH_LOGOUT_FAILED("AUTH_LOGOUT_FAILED", "Logout failed"),
    AUTH_DUPLICATE_BOTH("AUTH_DUPLICATE_BOTH", "User and email both already exists."),
    AUTH_DUPLICATE_EMAIL("AUTH_DUPLICATE_EMAIL","Email already exists."),
    AUTH_DUPLICATE_USERNAME("AUTH_DUPLICATE_USERNAME","User name already exists"),
    AUTH_LOGIN_ERROR("AUTH_LOGIN_ERROR", "Login error ");


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
