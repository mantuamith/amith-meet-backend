package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecentReceivedMessageResponse {
    private String contactId;
    private String newMessage;
    private Long timestamp;
    private Integer nMessages; // Nullable count of unread messages
}
