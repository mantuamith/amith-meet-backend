// src/main/java/com/algomeet/contactservice/enums/ResponseCode.java
package com.algomeet.contactservice.enums;

import lombok.Getter;

@Getter
public enum ResponseCode {
    OK("OK", "response.ok"),
    CREATED("CREATED", "response.created"),

    AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "error.auth.unauthorized"),
    ACCESS_DENIED("ACCESS_DENIED", "error.auth.forbidden"),

    BAD_REQUEST("BAD_REQUEST", "error.bad_request"),
    NOT_FOUND("NOT_FOUND", "error.not_found"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "error.method_not_allowed"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "error.unsupported_media"),
    INTERNAL_ERROR("INTERNAL_ERROR", "error.internal");

    private final String code;
    /** i18n message key (base key; specific endpoints can pass more specific keys) */
    private final String defaultMsgKey;

    ResponseCode(String code, String defaultMsgKey) {
        this.code = code;
        this.defaultMsgKey = defaultMsgKey;
    }
}
