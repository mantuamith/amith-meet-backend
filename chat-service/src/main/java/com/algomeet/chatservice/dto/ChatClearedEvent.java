// STOMP event to the user who cleared the chat
package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class ChatClearedEvent {
    private String contactId;
    private long affected;
    private long clearedAt;     // epoch seconds
}
