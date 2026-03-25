package com.algomeet.xmpp.chatservice.stanza;

/**
 * Represents the XEP-0198 'Answer' (Ack) stanza: <a xmlns='urn:xmpp:sm:3' h='...'/>
 */
public record StreamAck(long h) {
    
    public String toXml() {
        return String.format("<a xmlns='urn:xmpp:sm:3' h='%d'/>", h);
    }
}