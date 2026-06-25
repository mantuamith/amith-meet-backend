package com.algomeet.signalservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageBackupResponse {
    private String messageId;
    private String stanzaId;
    private String userKey;
    private String senderKey;   
    private String receiverKey;
    private String encryptedMessage;
    private Long sentAt;
    private Long deliveredAt;
    private Long readAt;
    private Long deletedAt;
    private Boolean isHidden;
    private String targetMessageId;   
    private String replyToMessageId;     
    private Integer editCount;
    private Boolean isStartOfConversation;
    private Long timestamp;
    private String algorithm;
    private String version;
    private String salt;    
}