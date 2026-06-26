package com.algomeet.xmpp.chatservice.repository.projection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Projection interface for MucMessage to retrieve a lightweight summary of messages 
 * without loading heavy payload data like stanzaXml.
 */
public interface MucMessageView {
    
    UUID getId();
    
    UUID getMessageId();
    
    UUID getRoomId();
    
    UUID getFrom();
    
    UUID getTo();
    
    List<UUID> getHiddenFromUserKeys();
    
    Long getDeletedAt();
    
    Long getReadAt();
    
    UUID getUpdateCursorId();
    
    Instant getCreatedAt();
    
    Boolean getCountable();
    
    List<UUID> getMediaIds();
}
