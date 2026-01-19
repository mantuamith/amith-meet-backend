package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.signalservice.dto.SignedPreKeyRequest;
import com.algomeet.signalservice.dto.SignedPreKeyResponse;
import com.algomeet.signalservice.entity.SignedPreKey;
import com.algomeet.signalservice.entity.SignedPreKeyId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.SignedPreKeyRepository;

@ExtendWith(MockitoExtension.class)
class SignedPreKeyServiceTest {

    private static final UUID USER_KEY =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private SignedPreKeyRepository repository;

    @Mock
    private UserDeviceService userDeviceService;

    @InjectMocks
    private SignedPreKeyService service;

    private SignedPreKey entity;

    @BeforeEach
    void setup() {
        entity = new SignedPreKey();
        entity.setId(new SignedPreKeyId(USER_KEY, 1));
        entity.setSignedPreKeyId(10);
        entity.setPublicKey("BBSignedPreKeyPublicBase64==");
        entity.setSignature("MEUCIQSignedSignatureBase64==");
    }

    /* -------------------------------------------------
     * GET SIGNED PRE-KEY
     * ------------------------------------------------- */

    @Test
    void getById_success() {
        SignedPreKeyId id = new SignedPreKeyId(USER_KEY, 1);

        when(repository.findById(id))
                .thenReturn(Optional.of(entity));

        SignedPreKeyResponse response =
                service.getById(USER_KEY, 1);

        assertNotNull(response);
        assertEquals(entity.getSignedPreKeyId(), response.getSignedPreKeyId());
        verify(repository).findById(id);
    }

    @Test
    void getById_notFound() {
        SignedPreKeyId id = new SignedPreKeyId(USER_KEY, 1);

        when(repository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.getById(USER_KEY, 1));
    }

    /* -------------------------------------------------
     * UPDATE SIGNED PRE-KEY
     * ------------------------------------------------- */

    @Test
    void update_success() {
        SignedPreKeyId id = new SignedPreKeyId(USER_KEY, 1);

        SignedPreKeyRequest request = new SignedPreKeyRequest();
        request.setSignedPreKeyId(20);
        request.setPublicKey("BBUpdatedPublicKeyBase64==");
        request.setSignature("MEUCUpdatedSignatureBase64==");

        when(repository.findById(id))
                .thenReturn(Optional.of(entity));

        when(repository.save(any(SignedPreKey.class)))
                .thenReturn(entity);

        doNothing().when(userDeviceService)
                .markDeviceAsUpdated(USER_KEY, 1);

        SignedPreKeyResponse response =
                service.update(USER_KEY, 1, request);

        assertNotNull(response);
        assertEquals(20, entity.getSignedPreKeyId());
        assertEquals("BBUpdatedPublicKeyBase64==", entity.getPublicKey());
        assertEquals("MEUCUpdatedSignatureBase64==", entity.getSignature());

        verify(repository).save(entity);
        verify(userDeviceService)
                .markDeviceAsUpdated(USER_KEY, 1);
    }

    @Test
    void update_notFound() {
        SignedPreKeyId id = new SignedPreKeyId(USER_KEY, 1);

        SignedPreKeyRequest request = new SignedPreKeyRequest();
        request.setSignedPreKeyId(20);
        request.setPublicKey("BBUpdatedPublicKeyBase64==");
        request.setSignature("MEUCUpdatedSignatureBase64==");

        when(repository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.update(USER_KEY, 1, request));

        verify(repository, never()).save(any());
        verify(userDeviceService, never())
                .markDeviceAsUpdated(any(), anyInt());
    }
}
