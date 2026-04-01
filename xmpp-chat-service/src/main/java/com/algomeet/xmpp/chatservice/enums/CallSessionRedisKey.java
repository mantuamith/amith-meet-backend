package com.algomeet.xmpp.chatservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CallSessionRedisKey {
    /**
     * The ZSET used for scheduling the 30-second timeout tasks.
     */
    DELAYED_QUEUE("delayed_missed_call_tasks"),

    /**
     * The prefix for the Hash storing call metadata (to, from, tenantId).
     */
    CALL_PENDING_PREFIX("calls:pending:");

    private final String val;

    /**
     * Helper to build a specific metadata key for a session.
     * Use this instead of manual string concatenation.
     */
    public String format(String sid) {
        if (this == CALL_PENDING_PREFIX) {
            return this.val + sid;
        }
        return this.val;
    }
}