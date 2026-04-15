package com.algomeet.groupservice.enums;

import com.algomeet.groupservice.util.MessageUtil;

public enum ResponseCode {
	AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
    SUCCESS("SUCCESS", "success"),
    
    GROUP_ID_NOT_FOUND("GROUP_ID_NOT_FOUND", "group.id.not-found"),
    USER_ALREADY_GROUP_MEMBER("USER_ALREADY_GROUP_MEMBER", "group.user-already-member"),
    GROUP_MEMBER_NOT_FOUND("GROUP_MEMBER_NOT_FOUND", "group.member-not-found"),
    GROUP_INVITE_CODE_INVALID("GROUP_INVITE_CODE_INVALID", "group.invite-code-invalid"),
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
