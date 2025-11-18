package com.algomeet.opaqueservice.enums;

import com.algomeet.opaqueservice.util.MessageUtil;

public enum ResponseCode {
	AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
    SUCCESS("SUCCESS", "success"),
    SECRET_KEY_NOT_FOUND("SECRET_KEY_NOT_FOUND", "secret.key.not-found"),
    SECRET_KEY_FORBIDDEN_ACCESS("SECRET_KEY_FORBIDDEN_ACCESS", "secret.key.forbidden-access");
	
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
