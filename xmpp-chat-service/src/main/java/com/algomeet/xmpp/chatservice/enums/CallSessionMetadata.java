package com.algomeet.xmpp.chatservice.enums;

public enum CallSessionMetadata {
	SID("sid"),
    TO_JID("to"),
    FROM_JID("from"),
    CALL_TYPE("callType"),
    TENANT_ID("tenantId"),
    START_TIME("startTime"),
    GROUP_ID("groupId"),
    USERNAME("username");

    private final String key;

    CallSessionMetadata(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}