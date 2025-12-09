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
    private String receiverKey;
    private String encryptedMessage;
    private Instant timestamp;
    private String algorithm;
    private String version;
    private String salt;

    public static MessageBackupResponse from(MessageBackupDocument doc) {
        return MessageBackupResponse.builder()
                .messageId(doc.getMessageId())
                .userKey(doc.getUserKey())
                .senderKey(doc.getSenderKey())
                .receiverKey(doc.getReceiverKey())
                .receiverKey(doc.getReceiverKey())
                .encryptedMessage(doc.getEncryptedMessage())
                .algorithm(doc.getAlgorithm())
                .version(doc.getVersion())
                .salt(doc.getSalt())
                .timestamp(doc.getTimestamp())
                .build();
    }
}