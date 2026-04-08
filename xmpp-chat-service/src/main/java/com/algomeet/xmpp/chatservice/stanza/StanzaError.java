package com.algomeet.xmpp.chatservice.stanza;

import com.algomeet.xmpp.chatservice.enums.XmppErrorType;

/**
 * Represents an XMPP Stanza Error as per RFC 6121.
 */
public record StanzaError(
    String id, 
    String to, 
    String from, 
    XmppErrorType errorType,
    String condition, 
    String text
) {
    public String toXml() {
        return String.format(
            "<message from='%s' to='%s' id='%s' type='error'>" +
            "  <error type='%s'>" +
            "    <%s xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>" +
            "    <text xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>%s</text>" +
            "  </error>" +
            "</message>",
            from, to, id, errorType.getValue(), condition, text
        );
    }
}