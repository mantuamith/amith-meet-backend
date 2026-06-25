package com.algomeet.signalservice.repository.projection;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;

public interface MessageBackupView {
	UUID getMessageId();
	/**
     * Extracts the stanzaId from the composite primary key (_id)
     */
    @Value("#{target.id.stanzaId}")
    UUID getStanzaId();
    
    /**
     * Extracts the userKey from the composite primary key (_id) 
     * (Adding this since it's now part of your identity schema, useful for purges)
     */
    @Value("#{target.id.userKey}")
    UUID getUserKey();
    UUID getSenderKey();
    UUID getReceiverKey();
    Long getSize();
    UUID getTargetMessageId();
    List<UUID> getMediaIds();
}