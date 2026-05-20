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
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.MessageBackupRepository;
import com.algomeet.signalservice.util.SecurityUtil;
import com.github.f4b6a3.uuid.UuidCreator;

@ExtendWith(MockitoExtension.class)
class MessageBackupServiceTest {

	@Mock
	private MessageBackupRepository repository;

	@InjectMocks
	private MessageBackupService service;

	private MessageBackupDocument document;
	
	@Mock
	private MediaService mediaService;
	
	@Mock
	private StringRedisTemplate redisTemplate;
	
	@Mock
	private MongoTemplate mongoTemplate;
	
	@Mock
	private ValueOperations<String, String> valueOperations;

	private UUID messageId;
	private UUID userKey;
	private UUID senderKey;
	private UUID receiverKey;
	
	@BeforeEach
	void setup() {
		document = new MessageBackupDocument();
		messageId = UuidCreator.getTimeOrderedEpoch();
		userKey = UuidCreator.getTimeOrderedEpoch();
		senderKey = UuidCreator.getTimeOrderedEpoch();
		receiverKey = UuidCreator.getTimeOrderedEpoch();
		
		document.setMessageId(messageId);
		document.setUserKey(userKey);
		document.setSenderKey(senderKey);
		document.setReceiverKey(receiverKey);
		document.setEncryptedMessage("ENCRYPTED_PAYLOAD");
		document.setAlgorithm("AES/GCM/NoPadding");
		document.setVersion("v1");
		document.setSalt("U0FMVA==");
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

	        MessageBackupDocument result = service.insert(document);

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
		when(repository.findById(messageId))
		.thenReturn(Optional.of(document));

		MessageBackupDocument result =
				service.getMessage(userKey, messageId);

		assertNotNull(result);
		assertEquals("msg-1", result.getMessageId());
	}

	@Test
	void getMessage_notFound() {
		when(repository.findById(UuidCreator.getTimeOrderedEpoch()))
		.thenReturn(Optional.empty());

		assertThrows(RecordNotFoundException.class,
				() -> service.getMessage(userKey, UuidCreator.getTimeOrderedEpoch()));
	}

	/* -------------------------------------------------
	 * GET MESSAGES (BULK)
	 * ------------------------------------------------- */

	@Test
	void getMessages_success() {
		when(repository.findAllById(List.of(messageId, UuidCreator.getTimeOrderedEpoch())))
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
		existingMsg.setUserKey(userKey2);
		existingMsg.setMessageId(messageId);
		existingMsg.setSize(50L);
		
		when(repository.findById(messageId))
		.thenReturn(Optional.of(existingMsg));
		

		MessageBackupDocument update = new MessageBackupDocument();
		update.setUserKey(userKey2);
		update.setEncryptedMessage("UPDATED_PAYLOAD");
		update.setSenderKey(senderKey2);
		update.setReceiverKey(receiverKey2);
		update.setAlgorithm("AES-CBC");
		update.setVersion("v2");
		update.setSalt("TkVXX1NBTFQ=");
		update.setSize(50L);
		
		when(repository.save(any(MessageBackupDocument.class)))
		.thenReturn(update);

		MessageBackupDocument result =
				service.update(userKey, messageId, update);

		assertEquals("msg-1", result.getMessageId());
		verify(repository).save(any(MessageBackupDocument.class));
	}

	@Test
	void update_notFound() {
		when(repository.findById(messageId))
		.thenReturn(Optional.empty());

		assertThrows(RecordNotFoundException.class,
				() -> service.update(userKey, messageId, document));

		verify(repository, never()).save(any());
	}

	/* -------------------------------------------------
	 * DELETE
	 * ------------------------------------------------- */

	@Test
	void delete_success() {
		MessageBackupDocument existingMsg = new MessageBackupDocument();
		UUID userKey2 = UuidCreator.getTimeOrderedEpoch();

		
		existingMsg.setUserKey(userKey2);
		existingMsg.setMessageId(messageId);
		existingMsg.setSize(50L);
		
		when(repository.findById(messageId))
		.thenReturn(Optional.of(existingMsg));

		doNothing().when(repository).deleteById(messageId);

		service.delete(userKey, messageId);

		verify(repository).deleteById(messageId);
	}

	@Test
	void delete_notFound() {
		when(repository.findById(messageId))
		.thenReturn(Optional.empty());

		assertThrows(RecordNotFoundException.class,
				() -> service.delete(userKey, messageId));

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

		service.deleteConversation(userKey, peerKey);

		verify(repository)
		.deleteByUserKeyAndConversationId(userKey, peerKey.toString());
	}

	/* -------------------------------------------------
	 * DELETE BY USER KEY
	 * ------------------------------------------------- */

	@Test
	void deleteByUserKey_success() {
		doNothing().when(repository)
		.deleteByUserKey(userKey);

		service.deleteByUserKey(userKey);

		verify(repository)
		.deleteByUserKey(userKey);
	}
}
