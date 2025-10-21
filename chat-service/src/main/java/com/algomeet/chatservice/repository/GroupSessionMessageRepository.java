package com.algomeet.chatservice.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.algomeet.chatservice.document.GroupSessionMessageDocument;
import com.algomeet.chatservice.document.MessageDocument;

public interface GroupSessionMessageRepository extends MongoRepository<GroupSessionMessageDocument, String> {
	void deleteByCorrelationId(String correlationId);
	
    List<MessageDocument> findByTo(String receiver, Pageable pageable);

    List<MessageDocument> findByTo(String receiver);      
}
