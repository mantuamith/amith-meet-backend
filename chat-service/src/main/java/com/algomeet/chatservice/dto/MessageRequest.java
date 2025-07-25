package com.algomeet.chatservice.dto;

import com.algomeet.chatservice.model.MessageType;

public class MessageRequest {
    private Long senderId;
    private Long receiverId;
    private Long groupId;
    private String content;
    private MessageType type;
}


