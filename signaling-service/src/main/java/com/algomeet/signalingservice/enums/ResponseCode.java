package com.algomeet.signalingservice.enums;

import com.algomeet.signalingservice.util.MessageUtil;

public enum ResponseCode {
	AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
    SUCCESS("SUCCESS", "success"),
	IDENTITY_KEY_REGISTER_SUCCESS("IDENTITY_KEY_REGISTER_SUCCESS", "identity-key.register.sucess"),
	IDENTITY_KEY_ALREADY_EXISTS("IDENTITY_KEY_ALREADY_EXISTS", "identity-key.register.already-exists"),	
	IDENTITY_KEY_REGISTER_FAILED("IDENTITY_KEY_REGISTER_FAILED", "identity-key.register.failed"),
	IDENTITY_KEY_UPDATE_SUCCESS("IDENTITY_KEY_UPDATE_SUCCESS", "identity-key.update.sucess"),
	IDENTITY_KEY_NOT_FOUND("IDENTITY_KEY_NOT_FOUND", "identity-key.not-found"),
	ONE_TIME_KEY_ADD_SUCCESS("ONE_TIME_KEY_ADD_SUCCESS", "one-time-key.add.sucess"),
	ONE_TIME_KEY_ADD_FAILED("ONE_TIME_KEY_ADD_FAILED", "one-time-key.add.failed"),
	ONE_TIME_KEY_DELETE_SUCCESS("ONE_TIME_KEY_DELETE_SUCCESS", "one-time-key.delete.success"),
	ONE_TIME_KEY_ID_NOT_FOUND("ONE_TIME_KEY_ID_NOT_FOUND", "one-time-key.id-not-found"),
	USER_KEY_ALREADY_EXISTS("USER_KEY_ALREADY_EXISTS", "identity-key.register.user-key-already-exists"),
	USER_KEY_NOT_FOUND("USER_KEY_NOT_FOUND", "user-key.not-found"),
	ONE_TIME_KEY_IS_NOT_AVAILABLE("ONE_TIME_KEY_IS_NOT_AVAILABLE", "one-time-key.not-available"),
	ONE_TIME_KEY_ALREADY_EXISTS("ONE_TIME_KEY_ALREADY_EXISTS", "one-time-key.key-already-exists"),
	ONE_TIME_KEY_RESERVED_MAX_LIMIT_EXCEEDED("ONE_TIME_KEY_RESERVED_MAX_LIMIT_EXCEEDED", "one-time-key.reserved-max-limit-exceeded"),
	USER_KEYS_BACKUP_SUCCESS("USER_KEYS_BACKUP_SUCCESS", "user-keys-backup.success"),
	USER_KEYS_BACKUP_NOT_FOUND("USER_KEYS_BACKUP_NOT_FOUND", "user-keys-backup.not-found");
	
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
