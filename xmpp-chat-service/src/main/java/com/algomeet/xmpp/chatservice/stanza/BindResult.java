package com.algomeet.xmpp.chatservice.stanza;

/**
 * Represents an XMPP Bind Result (RFC 6120).
 * Sent to the client after successful resource binding.
 */
import java.time.Instant;

/**
 * Represents an XMPP Bind Result (RFC 6120) with server time synchronization.
 */
public record BindResult(String id, String jid, String sessionId, String timestamp) {

    /**
     * Constructor that automatically generates a UTC timestamp.
     */
    public BindResult(String id, String jid, String sessionId) {
        this(id, jid, sessionId, Instant.now().toString());
    }

    /**
     * Default constructor for standard bind results.
     */
    public BindResult(String jid, String sessionId) {
        this("bind_1", jid, sessionId, Instant.now().toString());
    }

    public String toXml() {
        return String.format(
            "<iq type='result' id='%s'>" +
            "  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>" +
            "    <jid>%s</jid>" +
            "    <sessionid>%s</sessionid>" +
            "  </bind>" +
            "  <time xmlns='urn:xmpp:time'>" +
            "    <tzo>+00:00</tzo>" +
            "    <utc>%s</utc>" +
            "  </time>" +
            "</iq>",
            id, jid, sessionId, timestamp
        );
    }
}