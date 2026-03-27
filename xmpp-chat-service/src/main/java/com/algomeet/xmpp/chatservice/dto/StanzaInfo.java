package com.algomeet.xmpp.chatservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StanzaInfo {
    private String stanzaId;    // The 'id' attribute of the <message> or <presence>
    private String stanzaType;  // "message", "presence", or "iq"
    private String type;        // 'groupchat', 'chat', 'unavailable', etc.
    private String category;    // 'message', 'reaction', 'retraction', or 'error'
    private String targetId;    // The ID of the message being reacted to or deleted
    private String emoji;       // The actual emoji string (if reaction)
    private boolean isE2EE;     // True if OMEMO encryption is detected
}