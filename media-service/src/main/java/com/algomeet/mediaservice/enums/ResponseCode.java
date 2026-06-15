package com.algomeet.mediaservice.enums;

import com.algomeet.mediaservice.util.MessageUtil;

public enum ResponseCode {
	AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
    SUCCESS("SUCCESS", "success"),
    
    MEDIA_NOT_FOUND("MEDIA_NOT_FOUND", "media.not.found"),
    MEDIA_ACCESS_DENIED("MEDIA_ACCESS_DENIED", "media.access.denied"),
    MEDIA_FILE_TYPE_NOT_SUPPORTED("MEDIA_FILE_TYPE_NOT_SUPPORTED", "media.file.type.not-supported"),
    MEDIA_BATCH_UPLOAD_PARTIAL_FAILURE("MEDIA_BATCH_UPLOAD_PARTIAL_FAILURE", "media.batch.upload.partial-failure"),
    MEDIA_THUMBNAIL_NOT_AVAILABLE("MEDIA_THUMBNAIL_NOT_AVAILABLE", "media.thumbnail.not-available"),
    MEDIA_BATCH_LIMIT_EXCEEDED("MEDIA_BATCH_LIMIT_EXCEEDED", "media.batch.limit-exceeded")
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
