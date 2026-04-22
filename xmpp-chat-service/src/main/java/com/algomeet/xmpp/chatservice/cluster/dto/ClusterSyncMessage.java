package com.algomeet.xmpp.chatservice.cluster.dto;

import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;
import lombok.Data;

/**
 * <p>A Data Transfer Object (DTO) used for synchronizing XMPP stanzas across 
 * different nodes in the server cluster.</p>
 * 
 * <p>When a message is received by a node but the recipient is connected to a 
 * different physical server, this object is serialized (typically to JSON) 
 * and published to the cluster's message backbone (e.g., Redis Pub/Sub).</p>
 * 
 * <p><b>Fields:</b></p>
 * <ul>
 *     <li><b>id:</b> The unique stanza or message ID, critical for XEP-0198 acknowledgments.</li>
 *     <li><b>to:</b> The target UserKey or JID used to locate the session on the remote node.</li>
 *     <li><b>from:</b> The sender's UserKey or JID to maintain conversation context.</li>
 *     <li><b>payload:</b> The raw XML stanza or JSON content to be delivered to the client.</li>
 *     <li><b>timestamp:</b> Epoch time of when the synchronization event was created.</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterSyncMessage { 

    /**
     * The unique identifier for the stanza or message. 
     * Essential for tracking delivery status in the database.
     */
    private String id;

    /**
     * The recipient's unique identifier (UserKey or full JID).
     */
    private String to;

    /**
     * The sender's unique identifier (UserKey or full JID).
     */
    private String from;

    /**
     * The raw content to be routed. Usually valid XMPP XML, 
     * but can support JSON payloads for specialized extensions.
     */
    private String payload;
    
    /**
     * Message source 
     * 
     * "chat" or "groupchat" 
     */
    private ChatType chatType;  
    
    /**
     * Controls whether the receiving node is allowed to echo the stanza
     * back to sessions that belong to the original sender.
     *
     * <p><b>Purpose:</b></p>
     * <ul>
     *     <li>Used in multi-device or multi-session scenarios where the same user
     *         may be connected from multiple clients (mobile, web, desktop).</li>
     *     <li>Allows the message to be delivered to the sender's other active
     *         sessions for synchronization of sent messages.</li>
     *     <li>Can be disabled to prevent duplicate delivery to the originating
     *         device or to avoid unnecessary self-broadcast traffic.</li>
     * </ul>
     *
     * <p><b>Typical behavior:</b></p>
     * <ul>
     *     <li><code>true</code>  = echo to eligible sessions of the sender.</li>
     *     <li><code>false</code> = do not echo; route only to intended recipients.</li>
     * </ul>
     *
     * <p>This is especially useful when messages are routed across clustered
     * nodes and local session context is not available on the publishing node.</p>
     */
    private boolean isAllowEcho;

    /**
     * Identifies the specific client session that originated this cluster event.
     *
     * <p><b>Purpose:</b></p>
     * <ul>
     *     <li>Used to exclude the originating connection when echoing messages,
     *         preventing the sender from receiving duplicate copies.</li>
     *     <li>Helps target or filter delivery when a user has multiple active
     *         sessions across different devices or browser tabs.</li>
     *     <li>Useful for tracing, debugging, and correlating cluster-routed
     *         messages to a physical connection.</li>
     * </ul>
     *
     * <p><b>Examples:</b></p>
     * <ul>
     *     <li>WebSocket channel ID</li>
     *     <li>XMPP stream/session identifier</li>
     *     <li>Custom generated connection token</li>
     * </ul>
     *
     * <p>When combined with {@code isAllowEcho}, the system may echo the message
     * to the user's other sessions while skipping this originating session.</p>
     */
    private String sessionId;

    /**
     * The system time (in milliseconds) when this message was 
     * prepared for cluster synchronization.
     */
    @Builder.Default
    private long timestamp = System.currentTimeMillis(); 
}