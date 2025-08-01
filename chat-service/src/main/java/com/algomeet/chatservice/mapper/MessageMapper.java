package com.algomeet.chatservice.mapper;

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
                .timestamp(document.getTimestamp() != null ? document.getTimestamp().toEpochMilli() : null)
                .type(document.getType())
                .status(document.getStatus())
                .text(document.getContent())
                .mediaGroup(document.getMediaGroup())
                .meta(document.getMetaData())
                .failedRecipients(document.getFailedRecipients())
                .forwarded(document.getForwarded())
                .build();
    }
}
