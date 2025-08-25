// com.algomeet.chatservice.dto.DeliveryReceipt
package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class DeliveryReceipt {
    private String contactId;         // the receiver who confirms delivery
    private List<String> messageIds;  // delivered message IDs
    private long deliveredAt;         // epoch seconds
}
