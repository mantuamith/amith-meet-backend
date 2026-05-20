package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class ReadReceipt {
    private String contactId;         // who read them (user2)
    private String groupId;
    private List<String> messageIds;  // which messages got read
    private long atEpochSec;          // when it was marked READ
}

//TODO(migration):
// later, add contactKey or senderKey/receiverKey in addition to existing fields,
// and make the server accept either.
