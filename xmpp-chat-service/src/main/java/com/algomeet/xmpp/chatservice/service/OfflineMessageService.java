package com.algomeet.xmpp.chatservice.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class OfflineMessageService {	
    private final OfflineMessageRepository offlineMessageRepository;
    
	public Mono<OfflineMessage> save(String id, String to, String from, String type, String originalXml) {
		OfflineMessage offlineMessage = OfflineMessage.builder()
				.id(id)
				.to(to)
				.from(from)
				.messageType(type)
				.stanzaXml(originalXml)
				.build();
		
		return offlineMessageRepository.save(offlineMessage);
	}
	
	public List<OfflineMessage> getOfflineMessages(String to) {
		return offlineMessageRepository.findByToOrderByCreatedAtAsc(to);
	}
	
	public Mono<Void> deleteById(String ackMsgId) {
		return offlineMessageRepository.deleteById(ackMsgId);
	}
}
