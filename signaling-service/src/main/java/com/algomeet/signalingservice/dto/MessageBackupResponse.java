package com.algomeet.signalingservice.dto;

import java.time.Instant;

import com.algomeet.signalingservice.document.MessageBackupDocument;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageBackupResponse {
    private String messageId;
    private String userKey;
    private String senderKey;   
    private String encryptedMessage;
    private Instant timestamp;

    public static MessageBackupResponse from(MessageBackupDocument doc) {
        return MessageBackupResponse.builder()
                .messageId(doc.getMessageId())
                .userKey(doc.getUserKey())
                .senderKey(doc.getSenderKey())
                .encryptedMessage(doc.getEncryptedMessage())
                .timestamp(doc.getTimestamp())
                .build();
    }
}