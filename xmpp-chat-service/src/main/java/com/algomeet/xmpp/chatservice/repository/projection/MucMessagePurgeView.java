package com.algomeet.xmpp.chatservice.repository.projection;

import java.util.List;
import java.util.UUID;

public interface MucMessagePurgeView {
    
    UUID getId();
    
    UUID getMessageId();
    
    UUID getRoomId();
    
    List<UUID> getMediaIds();
    
    Long getDeletedAt();

}
