package com.algomeet.xmpp.chatservice.repository.projection;
import java.util.UUID;

public interface MucMessageMetadataProjection {
    UUID getId();
    UUID getRoomId();
}