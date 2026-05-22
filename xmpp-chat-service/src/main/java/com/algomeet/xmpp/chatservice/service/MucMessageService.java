package com.algomeet.xmpp.chatservice.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.document.MucRoomReadCursor;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;
import com.algomeet.xmpp.chatservice.mapper.MucMessageMapper;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.repository.MucRoomReadCursorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MucMessageService {    
	private final MucMessageRepository repository;
	private final MucMessageMapper mucMessageMapper;
	private final MucRoomReadCursorRepository mucRoomReadCursorRepository;

	public List<MucMessageResponse> getMessagesAfter(UUID userKey, UUID groupId, UUID afterStanzaId, int page, int size) { 
	    Pageable pageable = PageRequest.of(page, size);

	    // Collect into a standard ArrayList so it is safe to interact with during processing
	    List<MucMessageResponse> messages = 
	            repository.findByRoomIdAndIdGreaterThanAndToIsNullOrEqualtoUserkeyOrderByIdAsc(
	                    groupId, afterStanzaId, userKey, pageable)
	            .collectList()              
	            .blockOptional() 
	            .orElse(Collections.emptyList())
	            .stream()
	            .map(mucMessageMapper::toResponse)
	            .toList();

	    // Guard Clause: If there are no messages, return early and avoid IndexOutOfBoundsException
	    if (messages.isEmpty()) {
	        return Collections.emptyList();
	    }

	    // Map and process messages in a single clear pass
	    for (MucMessageResponse message : messages) {         
	        if (message.getHiddenFromUserKeys() != null && message.getHiddenFromUserKeys().contains(userKey)) {             
	            message.setIsHidden(true);
	            message.setHiddenFromUserKeys(null); // Lighten the load
	            message.setStanzaXml(null);         // Lighten the load
	        } 
	    }  

	    // Safe to access now that we proved the list is not empty
	    MucMessageResponse lastMessage = messages.get(messages.size() - 1);

	    List<UUID> readers = mucRoomReadCursorRepository.findByRoomIdAndLastReadMidGreaterThanEqual(
	            lastMessage.getRoomId(), lastMessage.getMessageId())
	            .collectList()
	            .blockOptional()
	            .orElse(Collections.emptyList())
	            .stream()
	            .map(MucRoomReadCursor::getUserKey)
	            .toList();

	    lastMessage.setReadByIds(readers);

	    // Lock it down before returning to the caller
	    return Collections.unmodifiableList(messages);
	}

	public List<MucMessageResponse> getMessagesBefore(UUID userKey, UUID groupId, UUID beforeStanzaId, int page, int size) {  
		Pageable pageable = PageRequest.of(page, size);

	    // Collect into a standard ArrayList so it is safe to interact with during processing
	    List<MucMessageResponse> messages = 
	            repository.findHistoricalMessages(groupId, beforeStanzaId, userKey, pageable)
	            .collectList()              
	            .blockOptional() 
	            .orElse(Collections.emptyList())
	            .stream()
	            .map(mucMessageMapper::toResponse)
	            .toList();

	    // Guard Clause: If there are no messages, return early and avoid IndexOutOfBoundsException
	    if (messages.isEmpty()) {
	        return Collections.emptyList();
	    }

	    // Map and process messages in a single clear pass
	    for (MucMessageResponse message : messages) {         
	        if (message.getHiddenFromUserKeys() != null && message.getHiddenFromUserKeys().contains(userKey)) {             
	            message.setIsHidden(true);
	            message.setHiddenFromUserKeys(null); // Lighten the load
	            message.setStanzaXml(null);         // Lighten the load
	        } 
	    }  
	    
	    // Safe to access now that we proved the list is not empty
	    MucMessageResponse lastMessage = messages.get(0);

	    List<UUID> readers = mucRoomReadCursorRepository.findByRoomIdAndLastReadMidGreaterThanEqual(
	            lastMessage.getRoomId(), lastMessage.getMessageId())
	            .collectList()
	            .blockOptional()
	            .orElse(Collections.emptyList())
	            .stream()
	            .map(MucRoomReadCursor::getUserKey)
	            .toList();

	    lastMessage.setReadByIds(readers);

	    // Lock it down before returning to the caller
	    return Collections.unmodifiableList(messages);
	}

	public List<MucMessageResponse> getMessageUpdates(UUID userKey, UUID groupId, UUID untilStanzaId, int page, 
	        int size) {    
		List<MucMessageResponse> messages = new ArrayList<>();
		
		if (page == 0) {
	        messages.add(getStartOfConversation(groupId));
	        size = size - 1;
	    }

		Pageable pageable = PageRequest.of(page, size);
		
		/**
		 * Retrieves message state updates (edit, delete, read, etc.)
		 * for the specified room up to and including the given stanza ID.
		 *
		 * Query conditions:
		 * - updateCursorId > untilStanzaId
		 *   Ensures only messages updated after the client's last known update cursor are returned.
		 *
		 * - id <= untilStanzaId
		 *   Prevents returning updates for messages beyond the requested synchronization boundary.
		 *
		 * Results are ordered ascending by message ID to preserve chronological update order.
		 */
		List<MucMessageResponse> modifiedMessages = repository.findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualOrderByIdAsc(
				groupId, untilStanzaId, untilStanzaId, pageable)
				.collectList()              
				.blockOptional() // Defensively handle an empty result safely
				.orElse(Collections.emptyList())
				.stream()
				.map(mucMessageMapper::toResponse)
				.toList(); // Returns an unmodifiable list

		if (modifiedMessages.isEmpty()) {
			return messages; // Fast return if there are no updates
		}

		// Map and process messages in a single clear pass
		for (MucMessageResponse message : modifiedMessages) {         
			if (message.getHiddenFromUserKeys() != null && message.getHiddenFromUserKeys().contains(userKey)) {             
				message.setIsHidden(true);
				message.setHiddenFromUserKeys(null); // Lighten the load
				message.setStanzaXml(null);         // Lighten the load
			} 
		}       

		// 3. Process the readers list for the final message
		MucMessageResponse lastMessage = modifiedMessages.get(modifiedMessages.size() - 1);

		List<UUID> readers = mucRoomReadCursorRepository.findByRoomIdAndLastReadMidGreaterThanEqual(
				lastMessage.getRoomId(), lastMessage.getMessageId())
				.collectList()
				.blockOptional()
				.orElse(Collections.emptyList())
				.stream()
				.map(MucRoomReadCursor::getUserKey)
				.toList();

		lastMessage.setReadByIds(readers);

		// 4. Combine into a defensively copied, unmodifiable result
		List<MucMessageResponse> result = new ArrayList<>(messages);
		result.addAll(modifiedMessages);
		return Collections.unmodifiableList(result);
	}

	private MucMessageResponse getStartOfConversation(UUID groupId) {
		MucMessage firstMessage = repository.findFirstByRoomIdOrderByIdAsc(groupId).block();		

		// Scenario A: The room has a history. Map the actual first message.
		if (firstMessage != null) {
			MucMessageResponse message = mucMessageMapper.toResponse(firstMessage);
			// Empty the payload to lighten the load
			message.setStanzaXml(null);
			message.setStartOfRoomConversation(true);
			return message;
		}

		// Scenario B: The room is brand new / completely empty. Return a structural anchor.
		MucMessageResponse emptyRoomAnchor = new MucMessageResponse();
		emptyRoomAnchor.setStanzaId(Constants.NIL_UUID);
		emptyRoomAnchor.setMessageId(groupId);
		emptyRoomAnchor.setStartOfRoomConversation(true);
		return emptyRoomAnchor;
	}
}
