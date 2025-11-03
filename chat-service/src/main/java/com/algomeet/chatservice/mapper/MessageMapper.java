package com.algomeet.chatservice.mapper;

import org.springframework.stereotype.Component;

import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.document.MessageResponse;

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
                .replyContent(document.getReplyContent())
                .encryptionMetadata(document.getEncryptionMetadata())
                .msgReadTimeStamp(document.getMsgReadTimeStamp())
                .msgDeliveredTimeStamp(document.getMsgDeliveredTimeStamp())
                .build();
    }
}
