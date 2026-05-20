// com.algomeet.chatservice.dto.DeliveryReceipt
package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class DeliveryReceipt {
    private String contactId;         // the receiver who confirms delivery
    private String groupId;
    private List<String> messageIds;  // delivered message IDs
    private long deliveredAt;         // epoch seconds
}

//TODO(migration):
// later, add contactKey or senderKey/receiverKey in addition to existing fields,
// and make the server accept either.
