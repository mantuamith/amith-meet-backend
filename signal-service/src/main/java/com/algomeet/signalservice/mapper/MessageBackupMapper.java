package com.algomeet.signalservice.mapper;

import java.time.Instant;
import java.util.UUID;

import com.algomeet.signalservice.dto.MessageBackupRequest;
import com.algomeet.signalservice.dto.MessageBackupResponse;
import com.algomeet.signalservice.repository.projection.MessageBackupView;
import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.document.MessageBackupKey;

public class MessageBackupMapper {

    public static MessageBackupDocument toEntity(UUID userKey, MessageBackupRequest request) {
        if (request == null) {
            return null;
        }
        
        MessageBackupDocument entity = new MessageBackupDocument();
        entity.setId(new MessageBackupKey(
                userKey,
                request.getStanzaId()
        ));
        
        entity.setMessageId(request.getMessageId());
        entity.setSenderKey(request.getSenderKey());
        entity.setReceiverKey(request.getReceiverKey());
        entity.setEncryptedMessage(request.getEncryptedMessage());
        entity.setSentAt(request.getSentAt());
        entity.setDeliveredAt(request.getDeliveredAt());
        entity.setReadAt(request.getReadAt());
        entity.setTargetMessageId(request.getTargetMessageId());
        entity.setReplyToMessageId(request.getReplyToMessageId());
        entity.setSize(request.getSize());
        entity.setAlgorithm(request.getAlgorithm());
        entity.setVersion(request.getVersion());
        entity.setSalt(request.getSalt());
        entity.setMediaIds(request.getMediaIds());
        
        if (request.getCreatedAt() != null) {
        	//Convert from ISO-8601 representation to Instant
        	Instant createdAtInstant = Instant.parse(request.getCreatedAt());
        	entity.setTimestamp(createdAtInstant);      	
        }

        return entity;
    }
    
    public static MessageBackupResponse from(MessageBackupDocument doc) {
        if (doc == null) {
            return null;
        }

        return MessageBackupResponse.builder()        		
                .messageId(getStringValue(doc.getMessageId()))  
                .stanzaId(getStringValue(doc.getId().getStanzaId()))
                .userKey(getStringValue(doc.getId().getUserKey()))
                .senderKey(getStringValue(doc.getSenderKey()))
                .receiverKey(getStringValue(doc.getReceiverKey()))
                .encryptedMessage(doc.getEncryptedMessage())
                
                // Fields that were missing in your snippet:
                .sentAt(doc.getSentAt())
                .deliveredAt(doc.getDeliveredAt())
                .readAt(doc.getReadAt())
                .deletedAt(doc.getDeletedAt())
                .isHidden(doc.getHiddenAt() != null)
                .targetMessageId(doc.getTargetMessageId() != null ? doc.getTargetMessageId().toString() : null)
                .replyToMessageId(doc.getReplyToMessageId() != null ? doc.getReplyToMessageId().toString() : null)
                .editCount(doc.getEditCount())
                .isStartOfConversation(doc.getStartOfConversation())
                // Meta info
                .algorithm(doc.getAlgorithm())
                .version(doc.getVersion())
                .salt(doc.getSalt())
                .timestamp(doc.getTimestamp().toEpochMilli())
                .build();
    }
    
    public static MessageBackupResponse from(MessageBackupView doc) {
        if (doc == null) {
            return null;
        }

        return MessageBackupResponse.builder()        		
                .messageId(getStringValue(doc.getMessageId()))  
                .stanzaId(getStringValue(doc.getStanzaId()))
                .userKey(getStringValue(doc.getUserKey()))
                .senderKey(getStringValue(doc.getSenderKey()))
                .targetMessageId(doc.getTargetMessageId() != null ? doc.getTargetMessageId().toString() : null)
                .build();
    }
    
    private static String getStringValue(UUID uuid) {
    	if(uuid == null) {
    		return null;
    	}
    	
    	return uuid.toString();
    }
}