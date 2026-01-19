package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.MessageBackupRepository;

@ExtendWith(MockitoExtension.class)
class MessageBackupServiceTest {

    @Mock
    private MessageBackupRepository repository;

    @InjectMocks
    private MessageBackupService service;

    private MessageBackupDocument document;

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
        when(repository.save(document)).thenReturn(document);

        MessageBackupDocument result = service.insert(document);

        assertNotNull(result);
        assertEquals("msg-1", result.getMessageId());
        verify(repository).save(document);
    }

    /* -------------------------------------------------
     * GET CONVERSATION
     * ------------------------------------------------- */

    @Test
    void getConversation_success() {
        Page<MessageBackupDocument> page =
                new PageImpl<>(List.of(document));

        when(repository.findConversation(
                eq("user-1"),
                eq("peer-1"),
                any(PageRequest.class)))
                .thenReturn(page);

        Page<MessageBackupDocument> result =
                service.getConversation("user-1", "peer-1", 0, 10);

        assertEquals(1, result.getTotalElements());
        verify(repository).findConversation(
                eq("user-1"),
                eq("peer-1"),
                any(PageRequest.class));
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
        when(repository.findById("msg-1"))
                .thenReturn(Optional.of(document));
        when(repository.save(any(MessageBackupDocument.class)))
                .thenReturn(document);

        MessageBackupDocument update = new MessageBackupDocument();
        update.setUserKey("user-2");
        update.setEncryptedMessage("UPDATED_PAYLOAD");
        update.setSenderKey("sender-2");
        update.setReceiverKey("receiver-2");
        update.setAlgorithm("AES-CBC");
        update.setVersion("v2");
        update.setSalt("TkVXX1NBTFQ=");

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
        when(repository.findById("msg-1"))
                .thenReturn(Optional.of(document));

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
