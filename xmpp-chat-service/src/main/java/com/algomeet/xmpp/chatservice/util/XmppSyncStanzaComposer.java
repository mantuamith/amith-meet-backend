package com.algomeet.xmpp.chatservice.util;

/**
 * Utility for composing XMPP headline stanzas used to propagate
 * conversation clearance events to a user's online devices.
 */
public class XmppSyncStanzaComposer {

    /**
     * Composes a MUC timeline sync headline stanza.
     */
    public static String createMucClearanceStanza(String domain, String roomId, String cutoffStanzaId, Boolean isGroupDeleted) {
        return new StringBuilder(256)
                .append("<message from='").append(domain).append("' type='headline'>")
                .append("<sync xmlns='urn:xmpp:algomeet:sync:history'>")
                .append("<conversation room-id='").append(roomId).append("' ")
                .append("cleared-until-stanza-id='").append(cutoffStanzaId).append("' ")
                .append("deleted='").append(isGroupDeleted.toString()).append("' />")
                .append("</sync>")
                .append("</message>")
                .toString();
    }

    /**
     * Composes a direct 1:1 chat timeline sync headline stanza.
     */
    public static String createDirectClearanceStanza(String domain, String peerKey, String cutoffStanzaId) {
        return new StringBuilder(256)
                .append("<message from='").append(domain).append("' type='headline'>")
                .append("<sync xmlns='urn:xmpp:algomeet:sync:history'>")
                .append("<conversation peer-key='").append(peerKey).append("' ") 
                .append("cleared-until-stanza-id='").append(cutoffStanzaId).append("' />")
                .append("</sync>")
                .append("</message>")
                .toString();
    }
}