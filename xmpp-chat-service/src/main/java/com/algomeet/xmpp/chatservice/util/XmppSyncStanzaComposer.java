package com.algomeet.xmpp.chatservice.util;

public class XmppSyncStanzaComposer {

    /**
     * Composes a MUC timeline sync headline stanza.
     */
    public static String createMucClearanceStanza(String domain, String roomId, Long clearedUntilTimestamp) {
        return new StringBuilder(256)
                .append("<message from='").append(domain).append("' type='headline'>")
                .append("<sync xmlns='urn:xmpp:algomeet:sync:history'>")
                .append("<conversation room-id='").append(roomId).append("' ") // FIX: Changed from 'from' to 'roomId'
                .append("cleared-until='").append(clearedUntilTimestamp).append("' />")
                .append("</sync>")
                .append("</message>")
                .toString();
    }

    /**
     * Composes a direct 1:1 chat timeline sync headline stanza.
     */
    public static String createDirectClearanceStanza(String domain, String senderKey, String cutoffMessageId) {
        return new StringBuilder(256)
                .append("<message from='").append(domain).append("' type='headline'>")
                .append("<sync xmlns='urn:xmpp:algomeet:sync:history'>")
                .append("<conversation peer-key='").append(senderKey).append("' ") // FIX: Changed from 'from' to 'senderKey'
                .append("cleared-until-message-id='").append(cutoffMessageId).append("' />")
                .append("</sync>")
                .append("</message>")
                .toString();
    }
}