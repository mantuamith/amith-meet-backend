package com.algomeet.chatservice.document;

import com.algomeet.chatservice.dto.GroupSessionPayload;
import com.algomeet.chatservice.model.GroupSessionMessageType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO used for requesting, sharing, and acknowledging group session keys.
 * 
 * Type meanings:
 *  - REQUEST: User asks another device for the current group session key.
 *  - SHARE: Sender provides the encrypted Megolm session key to recipients.
 *  - ACKNOWLEDGE: Recipient confirms successful receipt of a session key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSessionMessageResponse {
    private String id;             // Auto generated message ID
    
    /** 
     * Message Id of the parent message (
     * e.g 
     * 1. If REQUEST message is initiated, the response SHARE message correlation ID must be 
     * set using the REQUEST message id., then the ACKNOWLEDGE message must be using same correlation ID.
     * 
     * 2. If SHARE message is initiated without REQUEST, the ACKNOWLEDGE message correlation ID must be set using SHARE message ID.
     * )
     * 
     */
    private String correlationId;  
    private GroupSessionMessageType type;  // REQUEST, SHARE, ACKNOWLEDGE
    private String groupId;        // group or room identifier
    private String from;           // sender username
    private String fromKey;        // sender identity key or UUID
    private String to;             // target username
    private String toKey;          // target identity key or UUID

    private GroupSessionPayload payload; // message content (see below)
}