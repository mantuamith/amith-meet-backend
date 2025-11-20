package com.algomeet.controlservice.enums;

import com.algomeet.controlservice.util.MessageUtil;

public enum ResponseCode {
    AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "auth.session.revoked"),
	SUCCESS("SUCCESS", "success"),
    ADD_TENANT_SUCCESS("ADD_TENANT_SUCCESS", "tenant.add.success"),
    TENANT_ID_ALREADY_EXISTS("TENANT_ID_ALREADY_EXISTS", "tenant.id.exists"),
    UPDATE_TENANT_SUCCESS("UPDATE_TENANT_SUCCESS", "tenant.update.success"),
    DELETE_TENANT_SUCCESS("DELETE_TENANT_SUCCESS", "tenant.delete.success"),
	TENANT_ID_NOT_FOUND("TENANT_ID_NOT_FOUND", "tenant.id.not-found"),
	ADD_ROLE_SUCCESS("ADD_ROLE_SUCCESS", "role.add.success"),
	ROLE_ID_NOT_FOUND("ROLE_ID_NOT_FOUND", "role.id.not-found"),
	ROLE_ID_ALREADY_EXISTS("ROLE_ID_ALREADY_EXISTS", "role.id.exists"),
	ROLE_NAME_ALREADY_EXISTS("ROLE_NAME_ALREADY_EXISTS", "role.name.exists"),
	UPDATE_ROLE_SUCCESS("UPDATE_ROLE_SUCCESS", "role.update.success"),
    DELETE_ROLE_SUCCESS("DELETE_ROLE_SUCCESS", "role.delete.success"),
	;

    private final String code;
    private final String defaultMessage;

    ResponseCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return MessageUtil.getMessage(defaultMessage);
    }
}
