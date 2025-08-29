package com.algomeet.chatservice.dto;

import lombok.Data;

// STOMP delete command from client
@Data
public class MessageDeleteCommand {
    private String messageId;
    private boolean deleteForEveryone;  // same meaning
}
