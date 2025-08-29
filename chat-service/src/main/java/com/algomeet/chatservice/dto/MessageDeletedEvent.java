package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// STOMP event server -> clients
@AllArgsConstructor
@Data
public class MessageDeletedEvent {
    private String messageId;
    private String byUser;          // who initiated
    private boolean forEveryone;    // true if global delete
    private long deletedAt;         // epoch seconds
}
