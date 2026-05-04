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

	@BeforeEach
	void setup() {
		document = new MessageBackupDocument();
		document.setMessageId("msg-1");
		document.setUserKey("user-1");
		document.setSenderKey("sender-1");
		document.setReceiverKey("receiver-1");
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
		when(repository.findById("msg-1"))
		.thenReturn(Optional.of(document));

		MessageBackupDocument result =
				service.getMessage("msg-1");

		assertNotNull(result);
		assertEquals("msg-1", result.getMessageId());
	}

	@Test
	void getMessage_notFound() {
		when(repository.findById("missing"))
		.thenReturn(Optional.empty());

		assertThrows(RecordNotFoundException.class,
				() -> service.getMessage("missing"));
	}

	/* -------------------------------------------------
	 * GET MESSAGES (BULK)
	 * ------------------------------------------------- */

	@Test
	void getMessages_success() {
		when(repository.findAllById(List.of("msg-1", "msg-2")))
		.thenReturn(List.of(document));

		List<MessageBackupDocument> result =
				service.getMessages(List.of("msg-1", "msg-2"));

		assertEquals(1, result.size());
		verify(repository).findAllById(any());
	}

	/* -------------------------------------------------
	 * UPDATE
	 * ------------------------------------------------- */

	@Test
	void update_success() {
		MessageBackupDocument existingMsg = new MessageBackupDocument();
		existingMsg.setUserKey("user-2");
		existingMsg.setMessageId("msg-1");
		existingMsg.setSize(50L);
		
		when(repository.findById("msg-1"))
		.thenReturn(Optional.of(existingMsg));
		

		MessageBackupDocument update = new MessageBackupDocument();
		update.setUserKey("user-2");
		update.setEncryptedMessage("UPDATED_PAYLOAD");
		update.setSenderKey("sender-2");
		update.setReceiverKey("receiver-2");
		update.setAlgorithm("AES-CBC");
		update.setVersion("v2");
		update.setSalt("TkVXX1NBTFQ=");
		update.setSize(50L);
		
		when(repository.save(any(MessageBackupDocument.class)))
		.thenReturn(update);

		MessageBackupDocument result =
				service.update("msg-1", update);

		assertEquals("msg-1", result.getMessageId());
		verify(repository).save(any(MessageBackupDocument.class));
	}

	@Test
	void update_notFound() {
		when(repository.findById("msg-1"))
		.thenReturn(Optional.empty());

		assertThrows(RecordNotFoundException.class,
				() -> service.update("msg-1", document));

		verify(repository, never()).save(any());
	}

	/* -------------------------------------------------
	 * DELETE
	 * ------------------------------------------------- */

	@Test
	void delete_success() {
		MessageBackupDocument existingMsg = new MessageBackupDocument();
		existingMsg.setUserKey("user-2");
		existingMsg.setMessageId("msg-1");
		existingMsg.setSize(50L);
		
		when(repository.findById("msg-1"))
		.thenReturn(Optional.of(existingMsg));

		doNothing().when(repository).deleteById("msg-1");

		service.delete("msg-1");

		verify(repository).deleteById("msg-1");
	}

	@Test
	void delete_notFound() {
		when(repository.findById("msg-1"))
		.thenReturn(Optional.empty());

		assertThrows(RecordNotFoundException.class,
				() -> service.delete("msg-1"));

		verify(repository, never()).deleteById(any());
	}

	/* -------------------------------------------------
	 * DELETE CONVERSATION
	 * ------------------------------------------------- */

	@Test
	void deleteConversation_success() {
		doNothing().when(repository)
		.deleteConversation("user-1", "peer-1");

		service.deleteConversation("user-1", "peer-1");

		verify(repository)
		.deleteConversation("user-1", "peer-1");
	}

	/* -------------------------------------------------
	 * DELETE BY USER KEY
	 * ------------------------------------------------- */

	@Test
	void deleteByUserKey_success() {
		doNothing().when(repository)
		.deleteByUserKey("user-1");

		service.deleteByUserKey("user-1");

		verify(repository)
		.deleteByUserKey("user-1");
	}
}
