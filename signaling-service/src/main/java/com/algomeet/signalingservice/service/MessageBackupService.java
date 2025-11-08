package com.algomeet.signalingservice.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.algomeet.signalingservice.document.MessageBackupDocument;
import com.algomeet.signalingservice.repository.MessageBackupRepository;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@RequiredArgsConstructor
@Service
@Data
public class MessageBackupService {
	private MessageBackupRepository messageBackupRepository;
	
	public MessageBackupDocument insert(MessageBackupDocument backup) {
		return messageBackupRepository.insert(backup);
	}
	
	public Page<MessageBackupDocument> getBackupsByUserKeyAnSenderKey(String userKey, String senderKey, int page, int size) {
        return messageBackupRepository.findByUserKeyAndSenderKeyOrderByTimestampDesc(userKey, senderKey, PageRequest.of(page, size));
    }
	
	public MessageBackupDocument update(String messageId, MessageBackupDocument backup) {
		backup.setMessageId(messageId);
		Optional<MessageBackupDocument> updateOpt = messageBackupRepository.findById(messageId);
		
		if (updateOpt.isEmpty()) {
			throw new RuntimeException("Message ID not found");
		}
		
		return messageBackupRepository.save(updateOpt.map(b -> {
			b.setUserKey(backup.getUserKey());
			b.setEncryptedMessage(backup.getEncryptedMessage());
			b.setSenderKey(backup.getSenderKey());
			return b;
		}).get());
	}
			
	public void delete(String messageId) {
		messageBackupRepository.deleteById(messageId);
	}	
	
	public void deleteByUserKeyAnSenderKey(String userKey, String senderKey) {
		messageBackupRepository.deleteByUserKeyAndSenderKey(userKey, senderKey);
	}	
	
	public void deleteByUserKey(String userKey ){
		messageBackupRepository.deleteByUserKey(userKey);
	}		
}
