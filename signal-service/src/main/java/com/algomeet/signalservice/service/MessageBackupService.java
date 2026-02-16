package com.algomeet.signalservice.service;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.dto.StorageUsageAdjustmentRequest;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.MessageBackupRepository;
import com.algomeet.signalservice.repository.projection.ConversationStorageStats;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
@Data
public class MessageBackupService {
	private final MessageBackupRepository repository;
	private final MediaService mediaService;
	
	public MessageBackupDocument insert(MessageBackupDocument backup) {		
		// Set the message size
		if (backup.getSize() == null || backup.getSize() == 0) {
			backup.setSize(backup.getEncryptedMessage() != null 
					? backup.getEncryptedMessage().getBytes(Charset.forName("utf-8")).length : 0L);
		}
		
		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatMessageCountDelta(1L);
		req.setChatStorageBytesDelta(backup.getSize());
		mediaService.adjustStorageUsage(backup.getUserKey(), req);
		
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
		
		// Set the message size
		if (backup.getSize() == null || backup.getSize() == 0) {
			backup.setSize(backup.getEncryptedMessage() != null 
					? backup.getEncryptedMessage().getBytes(Charset.forName("utf-8")).length : 0L);
		}
		
		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatStorageBytesDelta(backup.getSize() - updateOpt.get().getSize());
		mediaService.adjustStorageUsage(backup.getUserKey(), req);
		
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
		
		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatMessageCountDelta(-1L);
		req.setChatStorageBytesDelta(-updateOpt.get().getSize());
		mediaService.adjustStorageUsage(updateOpt.get().getUserKey(), req);
		
		repository.deleteById(messageId);
	}	
	
	@Transactional
	public void deleteConversation(String userKey, String peerKey) {
		
		ConversationStorageStats stats =
                   repository.getConversationStorageStats(userKey, peerKey)
		                     .stream()
		                     .findFirst()
		                     .orElse(new ConversationStorageStats(0L, 0L)); // Or handle empty


		long totalSize = stats != null ? stats.getTotalSize() : 0L;
		long messageCount = stats != null ? stats.getMessageCount() : 0L;
			
		// Update user storage usage 
		StorageUsageAdjustmentRequest req = new StorageUsageAdjustmentRequest();
		req.setChatMessageCountDelta(-messageCount);
		req.setChatStorageBytesDelta(-totalSize);
		mediaService.adjustStorageUsage(userKey, req);
				
		
		repository.deleteConversation(userKey, peerKey);				
	}	
	
	public void deleteByUserKey(String userKey ){
		repository.deleteByUserKey(userKey);
		
		// Delete user storage usage
		mediaService.deleteStorage(userKey);
	}		
}
