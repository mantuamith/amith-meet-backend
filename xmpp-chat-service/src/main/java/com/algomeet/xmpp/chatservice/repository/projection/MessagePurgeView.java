package com.algomeet.xmpp.chatservice.repository.projection;

import java.util.List;
import java.util.UUID;

public interface MessagePurgeView {
    
    UUID getStanzaId();
    
    UUID getMessageId();
    
	UUID getFrom();        // Sender user key / ID

	UUID getTo();          // Receiver user key / ID
    
    List<UUID> getMediaIds();

}
