package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.signalservice.dto.KyberPreKeyRequest;
import com.algomeet.signalservice.dto.KyberPreKeyResponse;
import com.algomeet.signalservice.entity.KyberPreKey;
import com.algomeet.signalservice.entity.KyberPreKeyId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.KyberPreKeyRepository;

@ExtendWith(MockitoExtension.class)
class KyberPreKeyServiceTest {

    @Mock
    private KyberPreKeyRepository repository;

    @Mock
    private UserDeviceService userDeviceService;

    @InjectMocks
    private KyberPreKeyService service;

    private static final UUID USER_KEY =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    /* -------------------------------------------------
     * GET PRE-KEY
     * ------------------------------------------------- */

    @Test
    void getPreKey_success() {
        KyberPreKeyId id = new KyberPreKeyId(USER_KEY, 1);

        KyberPreKey entity = new KyberPreKey();
        entity.setId(id);
        entity.setKyberPreKeyId(10);
        entity.setPublicKey("Base64PublicKey==");
        entity.setSignature("Base64Signature==");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(repository.findById(id)).thenReturn(Optional.of(entity));

        KyberPreKeyResponse response = service.getPreKey(id);

        assertNotNull(response);
        assertEquals(10, response.getKyberPreKeyId());
        assertEquals("Base64PublicKey==", response.getPublicKey());
        assertEquals("Base64Signature==", response.getSignature());
    }

    @Test
    void getPreKey_notFound() {
        KyberPreKeyId id = new KyberPreKeyId(USER_KEY, 1);

        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.getPreKey(id)
        );
    }

    /* -------------------------------------------------
     * UPDATE PRE-KEY
     * ------------------------------------------------- */

    @Test
    void updatePreKey_success() {
        KyberPreKeyId id = new KyberPreKeyId(USER_KEY, 1);

        KyberPreKey entity = new KyberPreKey();
        entity.setId(id);
        entity.setKyberPreKeyId(10);
        entity.setPublicKey("OldPublicKey==");
        entity.setSignature("OldSignature==");

        KyberPreKeyRequest request = new KyberPreKeyRequest();
        request.setKyberPreKeyId(20);
        request.setPublicKey("NewPublicKey==");
        request.setSignature("NewSignature==");

        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any(KyberPreKey.class))).thenAnswer(inv -> inv.getArgument(0));

        KyberPreKeyResponse response = service.updatePreKey(id, request);

        assertNotNull(response);
        assertEquals(20, response.getKyberPreKeyId());
        assertEquals("NewPublicKey==", response.getPublicKey());
        assertEquals("NewSignature==", response.getSignature());

        verify(repository).save(entity);
        verify(userDeviceService)
                .markDeviceAsUpdated(USER_KEY, 1);
    }

    @Test
    void updatePreKey_notFound() {
        KyberPreKeyId id = new KyberPreKeyId(USER_KEY, 1);

        KyberPreKeyRequest request = new KyberPreKeyRequest();
        request.setKyberPreKeyId(20);
        request.setPublicKey("NewPublicKey==");
        request.setSignature("NewSignature==");

        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.updatePreKey(id, request)
        );
    }
}
