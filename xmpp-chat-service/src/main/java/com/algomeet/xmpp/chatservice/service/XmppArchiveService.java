package com.algomeet.xmpp.chatservice.service;

import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class XmppArchiveService {
    private final MucMessageRepository repository;

    public Mono<MucMessage> archiveEvent(String xml, StanzaInfo info, String roomId, String from, String internalId) {
        MucMessage event = MucMessage.builder()
        		.id(internalId)
                .stanzaId(info.getStanzaId()) // Original client ID
                .roomId(roomId)
                .from(from)
                .stanzaXml(xml)
                .category(info.getCategory())
                .refersTo(info.getTargetId()) // This is the 'refersTo' ID
                .isE2EE(info.isE2EE())
                .build();

        return repository.save(event);
    }
}