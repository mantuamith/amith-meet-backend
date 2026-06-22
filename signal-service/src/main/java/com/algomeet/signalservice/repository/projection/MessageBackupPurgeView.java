package com.algomeet.signalservice.repository.projection;

import java.util.List;
import java.util.UUID;

public interface MessageBackupPurgeView {
    
    UUID getStanzaId();
    
    UUID getMessageId();
    
	UUID getSenderKey();        // Sender user key / ID

	UUID getReceiverKey();          // Receiver user key / ID
    
    List<UUID> getMediaIds();
    
    Long getDeletedAt();
}
