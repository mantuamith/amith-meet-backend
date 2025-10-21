package com.algomeet.chatservice.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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
@Document(collection = "group_session_messages")
@CompoundIndexes({
    @CompoundIndex(name = "idx_receiver",
            def  = "{'receiver': 1}")
})
public class GroupSessionMessageDocument {
    @Id
    private String id;
    
    /** 
     * Message Id of the parent message (
     * e.g 
     * 1. If REQUEST message is initiated, the response SHARE message correlation ID must be 
     * set using the REQUEST message id., then the ACKNOWLEDGE message must be using same correlation ID.
     * 
     * 2. If SHARE message is initiated without REQUEST, the ACKNOWLEDGE message correlation ID must be set using SHARE message ID.
     * )
     */    
    @Field("correlationId")
    private String correlationId;
    
    private GroupSessionMessageType type;      // REQUEST, SHARE, ACKNOWLEDGE
    
    @Field("to")
    private String to;             // target username
    
    @Field("toKey")
    private String toKey;          // target identity key or UUID
    
    @Field("groupId")
    private String groupId;        // group or room identifier
    
    @Field("from")
    private String from;           // sender username
    
    @Field("fromKey")
    private String fromKey;        // sender identity key or UUID

    private GroupSessionPayload payload; // message content (see below)
    
    private Instant timestamp = Instant.now();
}