package com.algomeet.xmpp.chatservice.stanza;

import java.time.Instant;

/**
 * Represents an XMPP Bind Result (RFC 6120) with server time synchronization.
 * Includes Algomeet specific domain metadata for client-side configuration.
 */
public record BindResult(
    String id, 
    String jid, 
    String sessionId, 
    String timestamp,
    String domain,
    String groupChatDomain
) {

    /**
     * Primary constructor for complete bind results.
     */
    public BindResult(String id, String jid, String sessionId, String domain, String groupChatDomain) {
        this(id, jid, sessionId, Instant.now().toString(), domain, groupChatDomain);
    }

    /**
     * Minimal constructor for standard resource binding.
     */
    public BindResult(String jid, String sessionId, String domain, String groupChatDomain) {
        this("bind_1", jid, sessionId, Instant.now().toString(), domain, groupChatDomain);
    }

    /**
     * Converts the record into a standard XMPP IQ-Result stanza.
     * Note: Per RFC 6120, standard bind results only strictly require the <jid/>.
     * We include <sessionid/> and <time/> as Algomeet-specific stream extensions.
     */
    public String toXml() {
        return String.format(
            "<iq type='result' id='%s'>" +
            "  <bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'>" +
            "    <jid>%s</jid>" +
            "    <sessionid>%s</sessionid>" +
            "    <domain>%s</domain>" +
            "    <muc_domain>%s</muc_domain>" +
            "  </bind>" +
            "  <time xmlns='urn:xmpp:time'>" +
            "    <tzo>+00:00</tzo>" +
            "    <utc>%s</utc>" +
            "  </time>" +
            "</iq>",
            id, jid, sessionId, domain, groupChatDomain, timestamp
        );
    }
}