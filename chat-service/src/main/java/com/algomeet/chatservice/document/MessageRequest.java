package com.algomeet.chatservice.document;

import com.algomeet.chatservice.model.MessageType;

public class MessageRequest {
    private Long senderId;
    private Long receiverId;
    private String groupId;
    private String content;
    private MessageType type;
}

