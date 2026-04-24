package com.algomeet.xmpp.chatservice.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CallSessionRedisKey {
    /**
     * ZSET of pending direct-call timeouts.
     * score = expiration timestamp
     */
    DIRECT_CALL_TIMEOUT_QUEUE("xmpp:call:delayed-missed-call-tasks"),

    /**
     * ZSET of pending group/MUC call timeouts.
     * score = expiration timestamp
     */
    MUC_CALL_TIMEOUT_QUEUE("xmpp:call:muc:delayed-missed-call-tasks"),

    /**
     * HASH prefix containing temporary call session metadata.
     */
    CALL_METADATA_PREFIX("xmpp:call:metadata:");

    private final String val;
    private static final String MUC_SID_SEPARATOR = "_";

    /**
     * Helper to build a specific metadata key for a session.
     * Use this instead of manual string concatenation.
     */
    public String format(String sid) {
        if (this == CALL_METADATA_PREFIX) {
            return this.val + sid;
        }
        return this.val;
    }
    
    /**
     * Builds a unique MUC (Multi-User Chat) call session identifier
     * by combining the shared call SID with a specific participant key.
     *
     * <p>
     * Why this exists:
     * In group calls, a single Jingle SID may be shared across multiple
     * recipients/participants. To track each participant independently
     * (ringing state, timeout queue entry, missed call status, etc.),
     * we create a derived SID per user.
     * </p>
     *
     * <p>
     * Example:
     * sid = "abc123"
     * receiverUserKey = "user789"
     *
     * Result:
     * abc123_user789
     * </p>
     *
     * <p>
     * Common use cases:
     * - Redis timeout queue keys
     * - Per-user ringing state
     * - Missed call tracking
     * - Individual participant call logs
     * </p>
     *
     * @param sid original shared call session identifier
     * @param receiverUserKey unique user key of the participant
     * @return participant-scoped MUC session identifier
     */
    public static String getMucSid(String sid, String receiverUserKey) {
        return sid + MUC_SID_SEPARATOR + receiverUserKey;
    }
    
    public static String getSidFromMucSid(String mucSid) {
        return mucSid.split(MUC_SID_SEPARATOR)[0];
    }
}