package com.algomeet.chatservice.dto;

import com.algomeet.chatservice.document.EncrytionMetadata;
import com.algomeet.chatservice.model.MessageMediaType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RecentReceivedMessageResponse {
    private String contactId;
    private String newMessage;
    private MessageMediaType recentMessageMediaType;
    private Long timestamp;
    private Integer nMessages; // Nullable count of unread messages
    private List<EncrytionMetadata> encryptionMetadata;
}

//TODO(migration):
// later, add contactKey or senderKey/receiverKey in addition to existing fields,
// and make the server accept either.
