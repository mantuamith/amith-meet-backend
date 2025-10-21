package com.algomeet.chatservice.mapper;

import com.algomeet.chatservice.document.GroupSessionMessageDocument;
import com.algomeet.chatservice.document.GroupSessionMessageResponse;
import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public MessageResponse toResponse(MessageDocument document) {
        return MessageResponse.builder()
                .id(document.getId())
                .from(document.getSender())
                .to(document.getReceiver())
                .fromKey(document.getSenderKey())          // NEW
                .toKey(document.getReceiverKey())          // NEW
                .timestamp(document.getTimestamp() != null ? document.getTimestamp().toEpochMilli() : null)
                .type(document.getType())
                .clientMessageId(document.getClientMessageId())
                .status(document.getStatus())
                .text(document.getContent())
                .mediaGroup(document.getMediaGroup())
                .meta(document.getMetaData())
                .failedRecipients(document.getFailedRecipients())
                .forwarded(document.getForwarded())
                .encryptionMetadata(document.getEncryptionMetadata())
                .build();
    }
    
    public GroupSessionMessageResponse toResponse(GroupSessionMessageDocument document) {
    	return GroupSessionMessageResponse.builder()
    			.id(document.getId())
    			.type(document.getType())
    			.groupId(document.getGroupId())
    			.to(document.getTo())
    			.toKey(document.getToKey())
    			.from(document.getFrom())
    			.fromKey(document.getFromKey())
    			.payload(document.getPayload())
    			.build();		
    }
}
