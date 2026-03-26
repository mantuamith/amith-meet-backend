package com.algomeet.xmpp.chatservice.stanza;

/**
 * Represents an XMPP Error IQ stanza for service unavailability.
 * Used primarily when a Jingle session-initiate fails due to the recipient being offline.
 */
public record XmppErrorIq(
    String id, 
    String from, 
    String to, 
    String errorText
) {    
    /**
     * Generates the XML for a 'service-unavailable' error.
     * * @return Formatted XML string compliant with RFC 6120 and XEP-0166.
     */
    public String toXml() {
        return String.format(
            "<iq from='%s' to='%s' id='%s' type='error'>" +
                "<error type='cancel'>" +
                    "<service-unavailable xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>" +
                    "<text xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'>%s</text>" +
                "</error>" +
            "</iq>", 
            from, to, id, errorText
        );
    }
}