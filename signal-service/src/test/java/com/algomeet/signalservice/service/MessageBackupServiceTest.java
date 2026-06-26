package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.document.MessageBackupKey;
import com.algomeet.signalservice.dto.MessageBackupRequest;
import com.algomeet.signalservice.dto.MessageBackupUpdateRequest;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.MessageBackupMapper;
import com.algomeet.signalservice.repository.MessageBackupRepository;
import com.algomeet.signalservice.util.SecurityUtil;
import com.github.f4b6a3.uuid.UuidCreator;

@ExtendWith(MockitoExtension.class)
class MessageBackupServiceTest {

	@Mock
	private MessageBackupRepository repository;

	@InjectMocks
	private MessageBackupService service;

	private MessageBackupRequest request;
	
	private MessageBackupDocument document;
	
	@Mock
	private MediaService mediaService;
	
	@Mock
	private StringRedisTemplate redisTemplate;
	
	@Mock
	private MongoTemplate mongoTemplate;
	
	@Mock
	private ValueOperations<String, String> valueOperations;

	private UUID stanzaId;
	private UUID messageId;
	private UUID userKey;
	private UUID senderKey;
	private UUID receiverKey;
	
	@BeforeEach
	void setup() {
		request = new MessageBackupRequest();
		stanzaId = UuidCreator.getTimeOrderedEpoch();
		messageId = UuidCreator.getTimeOrderedEpoch();
		userKey = UuidCreator.getTimeOrderedEpoch();
		senderKey = UuidCreator.getTimeOrderedEpoch();
		receiverKey = UuidCreator.getTimeOrderedEpoch();
		
		request.setStanzaId(stanzaId);
		request.setMessageId(messageId);

		request.setSenderKey(senderKey);
		request.setReceiverKey(receiverKey);
		request.setEncryptedMessage("ENCRYPTED_PAYLOAD");
		request.setAlgorithm("AES/GCM/NoPadding");
		request.setVersion("v1");
		request.setSalt("U0FMVA==");
		
		document = MessageBackupMapper.toEntity(userKey, request);
	}

	/* -------------------------------------------------
	 * INSERT
	 * ------------------------------------------------- */

	@Test
	void insert_success() {
	    try (MockedStatic<SecurityUtil> mocked = Mockito.mockStatic(SecurityUtil.class)) {

	        mocked.when(SecurityUtil::getUserKey).thenReturn("user-123");

	        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
	        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(true);

	        when(repository.save(document)).thenReturn(document);

	        MessageBackupDocument result = service.insert(request);

	        assertNotNull(result);
	        assertEquals("msg-1", result.getMessageId());

	        verify(repository).save(document);
	    }
	}

	/* -------------------------------------------------
	 * GET MESSAGE
	 * ------------------------------------------------- */

	@Test
	void getMessage_success() {
		when(repository.findByMessageIdAndUserKey(messageId, userKey))
		.thenReturn(Optional.of(document));

		MessageBackupDocument result =
				service.getMessage(userKey, messageId);

		assertNotNull(result);
		assertEquals("msg-1", result.getMessageId());
	}

	@Test
	void getMessage_notFound() {
		when(repository.findByMessageIdAndUserKey(UuidCreator.getTimeOrderedEpoch(), userKey))
		.thenReturn(Optional.empty());

		assertThrows(RecordNotFoundException.class,
				() -> service.getMessage(userKey, UuidCreator.getTimeOrderedEpoch()));
	}

	/* -------------------------------------------------
	 * GET MESSAGES (BULK)
	 * ------------------------------------------------- */

	@Test
	void getMessages_success() {
		when(repository.findAllById(List.of(messageId, UuidCreator.getTimeOrderedEpoch()).stream()
				.map(id -> new MessageBackupKey(userKey, id)).toList()))
		.thenReturn(List.of(document));

		List<MessageBackupDocument> result =
				service.getMessages(List.of(messageId, UuidCreator.getTimeOrderedEpoch()));

		assertEquals(1, result.size());
		verify(repository).findAllById(any());
	}

	/* -------------------------------------------------
	 * UPDATE
	 * ------------------------------------------------- */

	@Test
	void update_success() {
		MessageBackupDocument existingMsg = new MessageBackupDocument();
		UUID userKey2 = UuidCreator.getTimeOrderedEpoch();
		UUID senderKey2 = UuidCreator.getTimeOrderedEpoch();
		UUID receiverKey2 = UuidCreator.getTimeOrderedEpoch();
		
		existingMsg.setId(new MessageBackupKey(userKey2, UuidCreator.getTimeOrderedEpoch()));
		existingMsg.setMessageId(messageId);
		existingMsg.setSize(50L);
		
		when(repository.findByMessageIdAndUserKey(messageId, userKey2))
		.thenReturn(Optional.of(existingMsg));
		

		MessageBackupUpdateRequest update = new MessageBackupUpdateRequest();
		update.setEncryptedMessage("UPDATED_PAYLOAD");
		update.setAlgorithm("AES-CBC");
		update.setVersion("v2");
		update.setSalt("TkVXX1NBTFQ=");
		update.setSize(50L);
		MessageBackupDocument updateResp = new MessageBackupDocument();
		updateResp.setEncryptedMessage("UPDATED_PAYLOAD");
		updateResp.setSenderKey(senderKey2);
		updateResp.setReceiverKey(receiverKey2);
		updateResp.setAlgorithm("AES-CBC");
		updateResp.setVersion("v2");
		updateResp.setSalt("TkVXX1NBTFQ=");
		updateResp.setSize(50L);
		
		
		when(repository.save(any(MessageBackupDocument.class)))
		.thenReturn(updateResp);

		MessageBackupDocument result =
				service.update(userKey, messageId, update);

		assertEquals("msg-1", result.getMessageId());
		verify(repository).save(any(MessageBackupDocument.class));
	}

	@Test
	void update_notFound() {
		when(repository.findByMessageIdAndUserKey(messageId, userKey))
		.thenReturn(Optional.empty());

		MessageBackupUpdateRequest update = new MessageBackupUpdateRequest();
		assertThrows(RecordNotFoundException.class,
				() -> service.update(userKey, messageId, update));

		verify(repository, never()).save(any());
	}

	/* -------------------------------------------------
	 * DELETE
	 * ------------------------------------------------- */

	@Test
	void delete_success() {
		MessageBackupDocument existingMsg = new MessageBackupDocument();
		UUID userKey2 = UuidCreator.getTimeOrderedEpoch();
		UUID stanzaId2 = UuidCreator.getTimeOrderedEpoch();
		
		existingMsg.setId(new MessageBackupKey(userKey2, UuidCreator.getTimeOrderedEpoch()));
		existingMsg.setMessageId(messageId);
		existingMsg.setSize(50L);
		
		when(repository.findByMessageIdAndUserKey(messageId, userKey2))
		.thenReturn(Optional.of(existingMsg));

		doNothing().when(repository).deleteById(new MessageBackupKey(messageId, stanzaId2));

		service.delete(userKey, List.of(messageId));

		verify(repository).deleteById(new MessageBackupKey(messageId, stanzaId2));
	}

	@Test
	void delete_notFound() {
		when(repository.findByMessageIdAndUserKey(messageId, userKey))
		.thenReturn(Optional.empty());

		assertThrows(RecordNotFoundException.class,
				() -> service.delete(userKey, List.of(messageId)));

		verify(repository, never()).deleteById(any());
	}

	/* -------------------------------------------------
	 * DELETE CONVERSATION
	 * ------------------------------------------------- */

	@Test
	void deleteConversation_success() {
		UUID peerKey = UuidCreator.getTimeOrderedEpoch();
		
		doNothing().when(repository)
		.deleteByUserKeyAndConversationId(userKey, peerKey.toString());

		service.deleteConversation(userKey, peerKey, UuidCreator.getTimeOrderedEpoch());

		verify(repository)
		.deleteByUserKeyAndConversationId(userKey, peerKey.toString());
	}
}
