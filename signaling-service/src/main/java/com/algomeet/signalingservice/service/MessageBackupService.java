package com.algomeet.signalingservice.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.algomeet.signalingservice.document.MessageBackupDocument;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.repository.MessageBackupRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Data
public class MessageBackupService {
	private final MessageBackupRepository repository;
	
	public MessageBackupDocument insert(MessageBackupDocument backup) {
		return repository.save(backup);
	}
	
	public Page<MessageBackupDocument> getConversation(String userKey, String peerKey, int page, int size) {
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        return repository.findConversation(userKey, peerKey, pageable);
    }
	
	public MessageBackupDocument getMessage(String messageId) {
		Optional<MessageBackupDocument> backupOpt = repository.findById(messageId);		
		return backupOpt.orElseThrow(() -> new RecordNotFoundException("Message ID not found"));
	}
	
	public List<MessageBackupDocument> getMessages(List<String> messageIds) {
		List<MessageBackupDocument> messageList = repository.findAllById(messageIds);		
		return messageList;
	}	
	
	public MessageBackupDocument update(String messageId, MessageBackupDocument backup) {
		backup.setMessageId(messageId);
		Optional<MessageBackupDocument> updateOpt = repository.findById(messageId);
		
		if (updateOpt.isEmpty()) {
			throw new RecordNotFoundException("Message ID not found");
		}
		
		return repository.save(updateOpt.map(b -> {
			b.setUserKey(backup.getUserKey());
			b.setEncryptedMessage(backup.getEncryptedMessage());
			b.setSenderKey(backup.getSenderKey());
			b.setReceiverKey(backup.getReceiverKey());
			b.setAlgorithm(backup.getAlgorithm());
			b.setVersion(backup.getVersion());
			b.setSalt(backup.getSalt());
			return b;
		}).get());
	}
			
	public void delete(String messageId) {
		Optional<MessageBackupDocument> updateOpt = repository.findById(messageId);		
		if (updateOpt.isEmpty()) {
			throw new RecordNotFoundException("Message ID not found");
		}
		
		repository.deleteById(messageId);
	}	
	
	public void deleteConversation(String userKey, String peerKey) {
		repository.deleteConversation(userKey, peerKey);
	}	
	
	public void deleteByUserKey(String userKey ){
		repository.deleteByUserKey(userKey);
	}		
}
