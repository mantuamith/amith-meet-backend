package com.algomeet.signalservice.util;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.signalservice.constant.Constants;
import com.algomeet.signalservice.publisher.MessageMediaDeleteEventPublisher;
import com.algomeet.signalservice.repository.MessageBackupRepository;
import com.algomeet.signalservice.repository.projection.MessageBackupView;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DeleteMediaUtil {
	private final MessageBackupRepository repository;
	private final MessageMediaDeleteEventPublisher messageMediaDeleteEventPublisher;
	
	public void deleteMediaFilesForHiddenMessages(List<UUID> messageBackupIds, UUID userKey) {
		List<MessageBackupView> messages = repository.findByMessageIdInAndUserKey(messageBackupIds, userKey);
		
		for (MessageBackupView msg : messages) {
			if(!CollectionUtils.isEmpty(msg.getMediaIds())) {
				messageMediaDeleteEventPublisher.publish(userKey.toString(), 
						msg.getMediaIds().stream().map(id -> id.toString()).collect(Collectors.toSet()), 
						Set.of(userKey.toString()), 
						null, 
						msg.getMessageId().toString());
			}
		}		
	}
	
	public void deleteMediaFilesForRetractedMessages(List<UUID> messageBackupIds, UUID userKey) {
		List<MessageBackupView> messages = repository.findByMessageIdInAndUserKey(messageBackupIds, userKey);
		
		for (MessageBackupView msg : messages) {
			if(!CollectionUtils.isEmpty(msg.getMediaIds())) {
				messageMediaDeleteEventPublisher.publish(userKey.toString(), 
						msg.getMediaIds().stream().map(id -> id.toString()).collect(Collectors.toSet()), 
						Set.of(msg.getSenderKey().toString(), msg.getReceiverKey().toString()), 
						null, 
						msg.getMessageId().toString());
			}
		}		
	}
	
	public void deleteMediaFilesForDeleteConversation(String conversationId, UUID lastStanzaId, UUID userKey) {
	    int pageSize = 500;
	    // Always fetch page 0, because we are shifting the query window instead
	    Pageable pageable = PageRequest.of(0, pageSize); 
	    
	    UUID currentLastStanzaId = lastStanzaId != null ? lastStanzaId : Constants.LARGEST_UUID_V7;
	    boolean firstPage = true;
	    
	    while (true) {
	        // Query messages with media files  	                                              
	    	List<MessageBackupView> messages = 
	    			firstPage ? 
	    					repository.findByConversationIdAndStanzaIdLessThanEqualAndDeletedAtIsNullAndHiddenAtIsNullAndMediaIdsIsNotNullOrderByStanzaIdDesc(
	    							conversationId, currentLastStanzaId, pageable)
	    					: repository.findByConversationIdAndStanzaIdLessThanAndDeletedAtIsNullAndHiddenAtIsNullAndMediaIdsIsNotNullOrderByStanzaIdDesc(
	    							conversationId, currentLastStanzaId, pageable);
	        
	        if (CollectionUtils.isEmpty(messages)) {
	            break; 
	        }
	        
	        for (MessageBackupView msg : messages) {
	            if (!CollectionUtils.isEmpty(msg.getMediaIds())) {
	                messageMediaDeleteEventPublisher.publish(
	                        userKey.toString(), 
	                        msg.getMediaIds().stream().map(UUID::toString).collect(Collectors.toSet()), 
	                        Set.of(userKey.toString()), 
	                        null, 
	                        msg.getMessageId().toString()
	                );
	            }
	        }

	        // Update the cursor to the stanzaId of the very last message in this batch
	        // (Assumes messages are sorted by stanzaId descending)
	        currentLastStanzaId = messages.get(messages.size() - 1).getStanzaId();	     
	        
	        firstPage = false;
	    }       
	}
}
