package com.algomeet.xmpp.chatservice.stanza;

/**
 * Represents the XEP-0198 'Answer' (Ack) stanza: <a xmlns='urn:xmpp:sm:3' id='...'/>.
 * Custom implementation using message ID.
 * 
 * TODO: Implement the h count in the future
 */
public record StreamAck(Long h) {    
    public String toXml() {
        return String.format("<a xmlns='urn:xmpp:sm:3' h='%d'/>", h);
    }
}