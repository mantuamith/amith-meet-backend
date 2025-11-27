package com.algomeet.signalservice.enums;

import com.algomeet.signalservice.util.MessageUtil;

public enum ResponseCode {
	AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
    SUCCESS("SUCCESS", "success"),
    USER_DEVICE_ID_NOT_FOUND("USER_DEVICE_ID_NOT_FOUND", "user-device.id-not-found"),
    SIGNED_PRE_KEY_NOT_FOUND("SIGNED_PRE_KEY_NOT_FOUND", "signed-pre-key.not-found"),
    KYBER_PRE_KEY_NOT_FOUND("KYBER_PRE_KEY_NOT_FOUND", "kyber-pre-key.not-found"),
    ONE_TIME_PRE_KEY_NOT_AVAILABLE("ONE_TIME_PRE_KEY_NOT_AVAILABLE", "one-time-pre-key.not-available"),
    USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND("USER_DEVICE_GROUP_SENDER_KEY_NOT_FOUND", "user-device-group-sender-key.not-found"),
    	
	// Updated
	IDENTITY_KEY_BACKUP_NOT_FOUND("IDENTITY_KEY_BACKUP_NOT_FOUND", "identity-key-backup.not-found"),	
	USER_SESSION_BACKUP_NOT_FOUND("USER_SESSION_BACKUP_NOT_FOUND", "user-session-backup.not-found"),
	GROUP_SESSION_BACKUP_NOT_FOUND("GROUP_SESSION_BACKUP_NOT_FOUND", "group-session-backup.not-found"),	
	MESSAGE_BACKUP_NOT_FOUND("MESSAGE_BACKUP_NOT_FOUND", "message-backup.not-found");
	
    private final String code;
    private final String message;

    ResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return MessageUtil.getMessage(message) ;
    }
}
