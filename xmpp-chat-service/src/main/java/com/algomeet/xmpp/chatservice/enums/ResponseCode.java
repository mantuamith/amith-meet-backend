package com.algomeet.xmpp.chatservice.enums;

import com.algomeet.xmpp.chatservice.util.MessageUtil;

public enum ResponseCode {
	AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
    SUCCESS("SUCCESS", "success"),
	ERROR("ERROR", "error"),
	MESSAGE_RETENTION_UPDATE_IN_PROGRESS("MESSAGE_RETENTION_UPDATE_IN_PROGRESS", "message-retention-update-inprogress"),;
	
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
