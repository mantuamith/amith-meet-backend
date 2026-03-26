package com.algomeet.xmpp.chatservice.session;

import java.io.Serializable;
import com.algomeet.xmpp.chatservice.enums.UserState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * <p>Represents the metadata and real-time state of an active XMPP connection.</p>
 * * <p>This object is serialized and stored in the {@code UserSessionRegistry} (Redis). 
 * It allows the cluster to track not just that a user is "online," but the 
 * specific status of each of their devices.</p>
 * * <p><b>Key Attributes:</b></p>
 * <ul>
 * <li><b>Session ID:</b> The unique identifier for the physical Netty channel.</li>
 * <li><b>User State:</b> The current XMPP presence (e.g., ACTIVE, AWAY, DND).</li>
 * <li><b>Updated At:</b> A Unix timestamp (UTC) used for heartbeat monitoring 
 * and stale session cleanup.</li>
 * </ul>
 * * @author Algomeet Core Team
 */
@Data
@Builder
@AllArgsConstructor
public class UserSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor required for Jackson de-serialization from Redis JSON.
     */
    public UserSession() {}
	
    /**
     * The unique ID of the Netty Channel. 
     * Used to route messages to the correct physical socket on a specific node.
     */
    private String sessionId;

    /**
     * The current availability of the user for this specific session.
     * Maps to XEP-0186 (Presence) and XEP-0085 (Chat States).
     */
    private UserState state;

    /**
     * Timestamp (epoch milliseconds) of the last activity or status change.
     * Crucial for detecting "zombie" sessions if a node crashes.
     */
    private Long updatedAt;
}