package com.algomeet.notificationservice.enums;

import com.algomeet.notificationservice.util.MessageUtil;

public enum ResponseCode {
    SUCCESS("SUCCESS", "Success"),
	USER_NOTIFICATION_ID_NOT_FOUND("USER_NOTIFICATION_ID_NOT_FOUND", "user-notification.id.not-found");

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
