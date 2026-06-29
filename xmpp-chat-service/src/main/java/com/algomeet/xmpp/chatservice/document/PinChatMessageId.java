package com.algomeet.xmpp.chatservice.document;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinChatMessageId implements Serializable {
    private static final long serialVersionUID = 1L;

    private String conversationId;
    private UUID messageId;
    private UUID pinnedBy;
}