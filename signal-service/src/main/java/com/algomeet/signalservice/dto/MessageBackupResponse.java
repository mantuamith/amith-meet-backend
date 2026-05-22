package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.algomeet.signalservice.document.MessageBackupDocument;
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
    private String refersTo;      
    private Integer editCount;
    private Boolean isStartOfConversation;
    private Instant timestamp;
    private String algorithm;
    private String version;
    private String salt;

    public static MessageBackupResponse from(MessageBackupDocument doc) {
        if (doc == null) {
            return null;
        }

        return MessageBackupResponse.builder()        		
                .messageId(getStringValue(doc.getMessageId()))  
                .stanzaId(getStringValue(doc.getStanzaId()))
                .userKey(getStringValue(doc.getUserKey()))
                .senderKey(getStringValue(doc.getSenderKey()))
                .receiverKey(getStringValue(doc.getReceiverKey()))
                .encryptedMessage(doc.getEncryptedMessage())
                
                // Fields that were missing in your snippet:
                .sentAt(doc.getSentAt())
                .deliveredAt(doc.getDeliveredAt())
                .readAt(doc.getReadAt())
                .deletedAt(doc.getDeletedAt())
                .refersTo(doc.getRefersTo())
                .editCount(doc.getEditCount())
                .isStartOfConversation(doc.getStartOfConversation())
                // Meta info
                .algorithm(doc.getAlgorithm())
                .version(doc.getVersion())
                .salt(doc.getSalt())
                .timestamp(doc.getTimestamp())
                .build();
    }
    
    private static String getStringValue(UUID uuid) {
    	if(uuid == null) {
    		return null;
    	}
    	
    	return uuid.toString();
    }
}