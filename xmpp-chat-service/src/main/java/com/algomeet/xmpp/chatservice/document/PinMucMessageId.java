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
public class PinMucMessageId implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID groupId;
    private UUID messageId;
    private UUID pinnedBy;
}