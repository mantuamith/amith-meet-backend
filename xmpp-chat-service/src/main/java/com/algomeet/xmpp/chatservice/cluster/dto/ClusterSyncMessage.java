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
     * Flag indicating if this message is an XEP-0280 Carbon Copy.
     * When TRUE, this stanza is a synchronization copy intended for other devices 
     * belonging to the same user (multi-device sync).
     */
    private Boolean isCarbonCopy;
    
    /** * The unique identifier of the originating WebSocket/XMPP session.
     * This is used exclusively when isCarbonCopy is TRUE to perform 'Echo Suppression,'
     * ensuring the server does not route a carbon copy back to the device that 
     * initially authored the message.
     */
    private String sessionId;

    /**
     * The system time (in milliseconds) when this message was 
     * prepared for cluster synchronization.
     */
    @Builder.Default
    private long timestamp = System.currentTimeMillis(); 
}