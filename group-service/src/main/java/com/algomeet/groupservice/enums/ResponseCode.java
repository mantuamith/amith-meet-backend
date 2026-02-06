package com.algomeet.groupservice.enums;

import com.algomeet.groupservice.util.MessageUtil;

public enum ResponseCode {
	AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
    SUCCESS("SUCCESS", "success"),
    
    MEDIA_NOT_FOUND("MEDIA_NOT_FOUND", "media.not.found"),
    MEDIA_ACCESS_DENIED("MEDIA_ACCESS_DENIED", "media.access.denied"),
    MEDIA_FILE_TYPE_NOT_SUPPORTED("MEDIA_FILE_TYPE_NOT_SUPPORTED", "media.file.type.not-supported")
    ;
	
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
