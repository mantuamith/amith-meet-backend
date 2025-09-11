package com.algomeet.authservice.enums;

public enum ResponseCode {

    // Registration

    AUTH_REGISTER_SUCCESS("AUTH_REGISTER_SUCCESS", "User registered successfully"),
    AUTH_REGISTER_FAILED("AUTH_REGISTER_FAILED", "Unexpected error during registration"),
    AUTH_DUPLICATE_REGISTER_REQUEST("AUTH_DUPLICATE_REGISTER_REQUEST", "User with this email/username already exists."),
    AUTH_DUPLICATE_BOTH("AUTH_DUPLICATE_BOTH", "User and email both already exist."),
    AUTH_DUPLICATE_EMAIL("AUTH_DUPLICATE_EMAIL","Email already exists."),
    AUTH_DUPLICATE_USERNAME("AUTH_DUPLICATE_USERNAME","User name already exists"),

    // Login / Refresh / Logout / Delete
    AUTH_LOGIN_SUCCESS("AUTH_LOGIN_SUCCESS", "Login Successful"),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid credentials"),
    AUTH_LOGIN_FAILED("AUTH_LOGIN_FAILED", "Unexpected error during login"),
    AUTH_REFRESH_SUCCESS("AUTH_REFRESH_SUCCESS", "Access token refreshed successfully"),
    AUTH_INVALID_REFRESH_TOKEN("AUTH_INVALID_REFRESH_TOKEN", "Invalid or expired refresh token"),
    AUTH_REFRESH_FAILED("AUTH_REFRESH_FAILED", "Unexpected error during token refresh"),
    AUTH_LOGOUT_SUCCESS("AUTH_LOGOUT_SUCCESS", "User logged out successfully"),
    AUTH_LOGOUT_FAILED("AUTH_LOGOUT_FAILED", "Logout failed"),
    AUTH_USER_DELETED("AUTH_USER_DELETED", "User Account De-activated Successfully"),
    AUTH_DELETE_FAILED("AUTH_DELETE_FAILED", "Account Deletion Failed"),
    AUTH_LOGIN_ERROR("AUTH_LOGIN_ERROR", "Login error "),
    AUTH_FORGOT_EXPIRED_OTP("AUTH_FORGOT_EXPIRED_OTP", "Otp Expired"),
    AUTH_FORGOT_INVALID_OTP("AUTH_FORGOT_INVALID_OTP", "Invalid Otp"),
    AUTH_FORGOT_ATTEMPTS_EXCEEDED("AUTH_FORGOT_ATTEMPTS_EXCEEDED" , "Attempts Exceeded" ),
    AUTH_FORGOT_CHANNEL_MISMATCH("AUTH_FORGOT_CHANNEL_MISMATCH", "OTP Channel Mismatch"),
    AUTH_FORGOT_INVALID_TICKET("AUTH_FORGOT_INVALID_TICKET", "Invalid password reset ticket"),
    AUTH_FORGOT_EXPIRED_TICKET("AUTH_FORGOT_EXPIRED_TICKET", "Password reset ticket expired"),
    AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "Your session was revoked (signed-in on another device)."),
    AUTH_DEVICE_LOCKED("AUTH_DEVICE_LOCKED", "This account is active on another device."),
    AUTH_DUPLICATE_PHONE("AUTH_DUPLICATE_PHONE", "Phone Already exists"),
    
    // Profile / security questions / user security questions
    SUCCESS("SUCCESS", "Success"),
	UPDATE_USER_PROFILE_SUCCESS("UPDATE_USER_PROFILE_SUCCESS", "User profile has been updated successfully"),
	ADD_SECURITY_QUESTION_SUCCESS("ADD_SECURITY_QUESTION_SUCCESS", "Security question has been added successfully"),
	UPDATE_SECURITY_QUESTION_SUCCESS("UPDATE_SECURITY_QUESTION_SUCCESS", "Security question has been updated successfully"),
	DELETE_SECURITY_QUESTION_SUCCESS("DELETE_SECURITY_QUESTION_SUCCESS", "Security question has been deleted successfully"),
	SECURITY_QUESTION_ID_EXISTS("SECURITY_QUESTION_ID_EXISTS", "Security question ID already exists"),
	
	ADD_USER_SECURITY_QUESTION_SUCCESS("ADD_USER_SECURITY_QUESTION_SUCCESS", "User security question(s) have been added successfully"),
	USER_SECURITY_QUESTION_ID_EXISTS("USER_SECURITY_QUESTION_ID_EXISTS", "User security question Id already exists"),
	USER_SECURITY_QUESTION_VERIFY_FAILED("USER_SECURITY_QUESTION_VERIFY_FAILED", "User security question Id doesn't exists");

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
