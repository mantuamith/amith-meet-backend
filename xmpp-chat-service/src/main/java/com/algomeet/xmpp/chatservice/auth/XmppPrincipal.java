package com.algomeet.xmpp.chatservice.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString(exclude = "userKey") // Security best practice: don't log keys/tokens
public class XmppPrincipal {
    private final String userKey;  // The internal UUID or DB Primary Key
    private final String username; // The local part (e.g., "romeo")
    private final Integer tenantId;
    private final String sessionId;
    private final String domain; // The server domain (e.g., "algomeet.com")
    
    /**
     * Helper to get the Full JID for XMPP routing.
     * @return A string like "userkey/username@domain"
     */
    public String getFullJid() {
        return String.format("%s@%s", userKey, domain);
    }
}