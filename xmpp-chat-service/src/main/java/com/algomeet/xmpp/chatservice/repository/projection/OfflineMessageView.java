package com.algomeet.xmpp.chatservice.repository.projection;
import java.util.UUID;

public interface OfflineMessageView {
    UUID getStanzaId();
}