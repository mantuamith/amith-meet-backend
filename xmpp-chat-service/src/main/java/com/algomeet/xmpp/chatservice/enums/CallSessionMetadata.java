package com.algomeet.xmpp.chatservice.enums;

public enum CallSessionMetadata {
    TO("to"),
    FROM("from"),
    CALL_TYPE("callType"),
    TENANT_ID("tenantId"),
    START_TIME("startTime"),
    USERNAME("username");

    private final String key;

    CallSessionMetadata(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}