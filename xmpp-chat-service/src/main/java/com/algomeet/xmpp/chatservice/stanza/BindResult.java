package com.algomeet.xmpp.chatservice.stanza;

/**
 * Represents an XMPP Bind Result (RFC 6120).
 * Sent to the client after successful resource binding.
 */
public record BindResult(String id, String jid, String sessionId) {

    /**
     * Default constructor for standard bind results.
     */
    public BindResult(String jid, String sessionId) {
        this("bind_1", jid, sessionId);
    }

    public String toXml() {
        return String.format(
            "<iq type='result' id='%s'>" +
            "  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>" +
            "    <jid>%s</jid>" +
            "    <sessionid>%s</sessionid>" +
            "  </bind>" +
            "</iq>",
            id, jid, sessionId
        );
    }
}