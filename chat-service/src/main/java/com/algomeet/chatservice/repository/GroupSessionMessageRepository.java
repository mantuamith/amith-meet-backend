package com.algomeet.chatservice.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.algomeet.chatservice.document.GroupSessionMessageDocument;
import com.algomeet.chatservice.document.MessageDocument;
import com.algomeet.chatservice.model.MessageStatus;

public interface GroupSessionMessageRepository extends MongoRepository<GroupSessionMessageDocument, String> {
	void deleteByCorrelationId(String correlationId);
	
    List<MessageDocument> findByReceiver(String receiver, Pageable pageable);

    List<MessageDocument> findByReceiver(String receiver);   

    List<MessageDocument> findByReceiverAndStatus(String receiver, MessageStatus status);
    
    
}
