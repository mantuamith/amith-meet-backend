package com.algomeet.authservice.enums;

import com.algomeet.authservice.util.MessageUtil;

public enum ResponseCode {

    // Registration

    AUTH_REGISTER_SUCCESS("AUTH_REGISTER_SUCCESS", "auth.register.success"),
    AUTH_REGISTER_FAILED("AUTH_REGISTER_FAILED", "auth.register.failed"),
    AUTH_DUPLICATE_REGISTER_REQUEST("AUTH_DUPLICATE_REGISTER_REQUEST", "auth.duplicate-register-request"),
    AUTH_DUPLICATE_BOTH("AUTH_DUPLICATE_BOTH", "auth.duplicate-both"),
    AUTH_DUPLICATE_EMAIL("AUTH_DUPLICATE_EMAIL","auth.duplicate-email"),
    AUTH_DUPLICATE_USERNAME("AUTH_DUPLICATE_USERNAME","auth.duplicate-username"),

    // Login / Refresh / Logout / Delete
    AUTH_LOGIN_SUCCESS("AUTH_LOGIN_SUCCESS", "auth.login.success"),
    AUTH_INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "auth.invalid-credentials"),
    AUTH_LOGIN_FAILED("AUTH_LOGIN_FAILED", "auth.login.failed"),
    AUTH_REFRESH_SUCCESS("AUTH_REFRESH_SUCCESS", "auth.refresh.success"),
    AUTH_INVALID_REFRESH_TOKEN("AUTH_INVALID_REFRESH_TOKEN", "auth.invalid-refresh-token"),
    AUTH_REFRESH_FAILED("AUTH_REFRESH_FAILED", "auth.refresh.failed"),
    AUTH_LOGOUT_SUCCESS("AUTH_LOGOUT_SUCCESS", "auth.logout.success"),
    AUTH_LOGOUT_FAILED("AUTH_LOGOUT_FAILED", "auth.logout.failed"),
    AUTH_USER_DELETED("AUTH_USER_DELETED", "auth.user.deleted"),
    AUTH_DELETE_FAILED("AUTH_DELETE_FAILED", "auth.delete-failed"),
    AUTH_LOGIN_ERROR("AUTH_LOGIN_ERROR", "auth.login.error"),
    AUTH_FORGOT_EXPIRED_OTP("AUTH_FORGOT_EXPIRED_OTP", "auth.forgot-expired-otp"),
    AUTH_FORGOT_INVALID_OTP("AUTH_FORGOT_INVALID_OTP", "auth.forgot-invalid-otp"),
    AUTH_FORGOT_ATTEMPTS_EXCEEDED("AUTH_FORGOT_ATTEMPTS_EXCEEDED" , "auth.forgot-attempts-exceeded" ),
    AUTH_FORGOT_CHANNEL_MISMATCH("AUTH_FORGOT_CHANNEL_MISMATCH", "auth.forgot-channel-mismatch"),
    AUTH_FORGOT_INVALID_TICKET("AUTH_FORGOT_INVALID_TICKET", "auth.forgot-invalid-ticket"),
    AUTH_FORGOT_EXPIRED_TICKET("AUTH_FORGOT_EXPIRED_TICKET", "auth.forgot-expired-ticket"),
    AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
    AUTH_DEVICE_LOCKED("AUTH_DEVICE_LOCKED", "auth.device.locked"),
    AUTH_DUPLICATE_PHONE("AUTH_DUPLICATE_PHONE", "auth.duplicate-phone"),
    
    // Profile / security questions / user security questions
    SUCCESS("SUCCESS", "success"),
	UPDATE_USER_PROFILE_SUCCESS("UPDATE_USER_PROFILE_SUCCESS", "user-profile.update.success"),
	ADD_SECURITY_QUESTION_SUCCESS("ADD_SECURITY_QUESTION_SUCCESS", "security-question.add.success"),
	UPDATE_SECURITY_QUESTION_SUCCESS("UPDATE_SECURITY_QUESTION_SUCCESS", "security-question.update.success"),
	DELETE_SECURITY_QUESTION_SUCCESS("DELETE_SECURITY_QUESTION_SUCCESS", "security-question.delete.success"),
	SECURITY_QUESTION_ID_EXISTS("SECURITY_QUESTION_ID_EXISTS", "security-question.id-exists"),
	
	ADD_USER_SECURITY_QUESTION_SUCCESS("ADD_USER_SECURITY_QUESTION_SUCCESS", "user-security-question.add.success"),
	USER_SECURITY_QUESTION_ID_EXISTS("USER_SECURITY_QUESTION_ID_EXISTS", "user-security-question.id-exists"),
	USER_SECURITY_QUESTION_VERIFY_FAILED("USER_SECURITY_QUESTION_VERIFY_FAILED", "user-security-question.verify.failed"),
	DELETE_USER_SECURITY_QUESTION_SUCCESS("DELETE_USER_SECURITY_QUESTION_SUCCESS", "user-security-question.delete.success");

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
        return MessageUtil.getMessage(defaultMessage);
    }
}
