package com.algomeet.xmpp.chatservice.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString(exclude = "userKey") // Security best practice: don't log keys/tokens
public class XmppPrincipal {
	private final String userKey;  // The ID used as localpart (e.g. UUID)
    private final String username; // Human-readable name (for UI/Display)
    private final Integer tenantId;
    private final String sessionId; // Used as the XMPP Resource
    private final String domain;

    /**
     * Returns the Bare JID (user@domain).
     * Used for database indexing and MUC room persistence.
     */
    public String getBareJid() {
        return String.format("%s@%s", userKey, domain);
    }

    /**
     * Returns the Full JID (user@domain/resource).
     * Required for real-time routing to this specific connection.
     */
    public String getFullJid() {
        return String.format("%s@%s/%s", userKey, domain, sessionId);
    }
}