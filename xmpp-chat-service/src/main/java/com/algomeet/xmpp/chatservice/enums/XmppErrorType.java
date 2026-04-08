package com.algomeet.xmpp.chatservice.enums;

/**
 * Defines the standard XMPP error types as specified in RFC 6120 Section 8.3.
 * These types determine how a client (e.g., AlgoMeet Mobile/Web) should 
 * respond to a failed request.
 */
public enum XmppErrorType {

    /**
     * Permanent error related to lack of permission or authentication.
     * The client should NOT retry the request without a change in credentials 
     * or permissions (e.g., the user is muted/visitor role).
     */
    AUTH("auth"),

    /**
     * Permanent error indicating the request cannot be performed.
     * Used when the request violates a fixed server policy or is logically invalid 
     * (e.g., sending a message to a non-existent room).
     */
    CANCEL("cancel"),

    /**
     * Temporary error indicating the server is currently unable to fulfill the request.
     * The client SHOULD retry after a delay (e.g., rate limiting or database timeouts).
     */
    WAIT("wait"),

    /**
     * Error indicating the request is valid but the data must be corrected before 
     * the server will accept it (e.g., a message body that is too large).
     */
    MODIFY("modify"),

    /**
     * Rare informational type indicating the action was processed but 
     * requires additional steps or follow-up by the client.
     */
    CONTINUE("continue");

    private final String value;

    XmppErrorType(String value) {
        this.value = value;
    }

    /**
     * Returns the raw string value as it should appear in the 'type' 
     * attribute of an XMPP error stanza.
     */
    public String getValue() {
        return value;
    }
}