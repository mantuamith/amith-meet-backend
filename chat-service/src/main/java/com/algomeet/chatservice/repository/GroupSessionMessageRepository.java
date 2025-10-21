package com.algomeet.chatservice.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.algomeet.chatservice.document.GroupSessionMessageDocument;

public interface GroupSessionMessageRepository extends MongoRepository<GroupSessionMessageDocument, String> {
	void deleteByCorrelationId(String correlationId);
	
    List<GroupSessionMessageDocument> findByTo(String receiver, Pageable pageable);

    List<GroupSessionMessageDocument> findByTo(String receiver);      
}
