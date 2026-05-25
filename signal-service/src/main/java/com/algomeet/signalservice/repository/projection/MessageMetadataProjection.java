package com.algomeet.signalservice.repository.projection;
import java.util.UUID;

public interface MessageMetadataProjection {
    UUID getStanzaId();
    UUID getSenderKey();
    UUID getReceiverKey();
}