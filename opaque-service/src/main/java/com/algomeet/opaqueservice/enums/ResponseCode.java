package com.algomeet.opaqueservice.enums;

import com.algomeet.opaqueservice.util.MessageUtil;

public enum ResponseCode {
	AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
    SUCCESS("SUCCESS", "success"),
    MASTER_SECRET_KEY_NOT_FOUND("MASTER_SECRET_KEY_NOT_FOUND", "master-secret-key.not-found"),
    MASTER_SECRET_KEY_ALRREADY_EXISTS("MASTER_SECRET_KEY_ALRREADY_EXISTS", "master-secret-key.already-exists"),
    MASTER_SECRET_KEY_FORBIDDEN_ACCESS("MASTER_SECRET_KEY_FORBIDDEN_ACCESS", "master-secret-key.forbidden-access"),
    MASTER_SECRET_KEY_TEMPORARILY_LOCKED("MASTER_SECRET_KEY_TEMPORARILY_LOCKED", "master-secret-key.temporarily-locked");
	
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
