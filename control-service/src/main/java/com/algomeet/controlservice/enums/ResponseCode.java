package com.algomeet.controlservice.enums;

public enum ResponseCode {
    AUTH_SESSION_REVOKED("AUTH_SESSION_REVOKED", "Your session was revoked (signed-in on another device)."),
	SUCCESS("SUCCESS", "Success"),
    ADD_TENANT_SUCCESS("ADD_TENANT_SUCCESS", "Tenant added successfully"),
    TENANT_ID_ALREADY_EXISTS("TENANT_ID_ALREADY_EXISTS", "Tenant Id already exists"),
    UPDATE_TENANT_SUCCESS("UPDATE_TENANT_SUCCESS", "Tenant updated successfully"),
    DELETE_TENANT_SUCCESS("DELETE_TENANT_SUCCESS", "Tenant deleted successfully"),
	TENANT_ID_NOT_FOUND("TENANT_ID_NOT_FOUND", "Tenant Id not found"),
	ADD_ROLE_SUCCESS("ADD_ROLE_SUCCESS", "Role added successfully"),
	ROLE_ID_NOT_FOUND("ROLE_ID_NOT_FOUND", "Role Id not found"),
	ROLE_ID_ALREADY_EXISTS("ROLE_ID_ALREADY_EXISTS", "Role Id already exists"),
	ROLE_NAME_ALREADY_EXISTS("ROLE_NAME_ALREADY_EXISTS", "Role name already exists"),
	UPDATE_ROLE_SUCCESS("UPDATE_ROLE_SUCCESS", "Role updated successfully"),
    DELETE_ROLE_SUCCESS("DELETE_ROLE_SUCCESS", "Role deleted successfully"),
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
        return defaultMessage;
    }
}
