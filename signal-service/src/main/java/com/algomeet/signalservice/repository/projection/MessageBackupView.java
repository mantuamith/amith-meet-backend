package com.algomeet.signalservice.repository.projection;
import java.util.UUID;

public interface MessageBackupView {
	UUID getMessageId();
    UUID getStanzaId();
    UUID getUserKey();
    UUID getSenderKey();
    UUID getReceiverKey();
    Long getSize();
}