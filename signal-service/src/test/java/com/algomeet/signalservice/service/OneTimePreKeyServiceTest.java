package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.signalservice.dto.OneTimePreKeyRequest;
import com.algomeet.signalservice.dto.OneTimePreKeyResponse;
import com.algomeet.signalservice.dto.OneTimePreKeysRequest;
import com.algomeet.signalservice.entity.OneTimePreKey;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.OneTimePreKeyRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;

@ExtendWith(MockitoExtension.class)
class OneTimePreKeyServiceTest {

    private static final UUID USER_KEY =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private OneTimePreKeyRepository repository;

    @Mock
    private UserDeviceRepository deviceRepository;

    @InjectMocks
    private OneTimePreKeyService service;

    private OneTimePreKey entity;

    @BeforeEach
    void setup() {
        entity = new OneTimePreKey();
        entity.setId(1L);
        entity.setUserKey(USER_KEY);
        entity.setDeviceId(1);
        entity.setPreKeyId(100);
        entity.setPublicKey("BBOGJp8xYQm+ZqY2X8V5w0a2N7r9A1F2E3D4C5B6A7=");
        entity.setUsed(false);
    }

    /* -------------------------------------------------
     * UPDATE
     * ------------------------------------------------- */

    @Test
    void update_success() {
        OneTimePreKeyRequest request = new OneTimePreKeyRequest();
        request.setPreKeyId(200);
        request.setPublicKey("QkFTRTY0VVBERVRFRF9LRVk=");

        when(repository.findById(1L))
                .thenReturn(Optional.of(entity));
        when(repository.save(any(OneTimePreKey.class)))
                .thenReturn(entity);

        OneTimePreKeyResponse response = service.update(1L, request);

        assertNotNull(response);
        assertEquals(200, response.getPreKeyId());

        verify(repository).save(any(OneTimePreKey.class));
    }

    @Test
    void update_notFound() {
        when(repository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.update(1L, new OneTimePreKeyRequest()));

        verify(repository, never()).save(any());
    }

    /* -------------------------------------------------
     * CREATE
     * ------------------------------------------------- */

    @Test
    void create_success() {
        OneTimePreKeyRequest preKeyRequest = new OneTimePreKeyRequest();
        preKeyRequest.setPreKeyId(100);
        preKeyRequest.setPublicKey("QkFTRTY0UFJFS0VZ");

        OneTimePreKeysRequest request = new OneTimePreKeysRequest();
        request.setPreKeys(List.of(preKeyRequest));

        when(deviceRepository.findById(new UserDeviceId(USER_KEY, 1)))
                .thenReturn(Optional.of(mock()));
        when(repository.saveAll(any()))
                .thenReturn(List.of(entity));

        List<OneTimePreKeyResponse> responses =
                service.create(USER_KEY, 1, request);

        assertNotNull(responses);
        assertEquals(1, responses.size());

        verify(repository).saveAll(any());
    }

    @Test
    void create_deviceNotFound() {
        OneTimePreKeysRequest request = new OneTimePreKeysRequest();
        request.setPreKeys(List.of(new OneTimePreKeyRequest()));

        when(deviceRepository.findById(new UserDeviceId(USER_KEY, 1)))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.create(USER_KEY, 1, request));

        verify(repository, never()).saveAll(any());
    }

    /* -------------------------------------------------
     * GET PREKEYS
     * ------------------------------------------------- */

    @Test
    void getPrekeys_success() {
        when(deviceRepository.findById(new UserDeviceId(USER_KEY, 1)))
                .thenReturn(Optional.of(mock()));
        when(repository.findByUserKeyAndDeviceId(USER_KEY, 1))
                .thenReturn(List.of(entity));

        List<OneTimePreKeyResponse> responses =
                service.getPrekeys(USER_KEY, 1);

        assertEquals(1, responses.size());
    }

    @Test
    void getPrekeys_empty() {
        when(deviceRepository.findById(new UserDeviceId(USER_KEY, 1)))
                .thenReturn(Optional.of(mock()));
        when(repository.findByUserKeyAndDeviceId(USER_KEY, 1))
                .thenReturn(null);

        List<OneTimePreKeyResponse> responses =
                service.getPrekeys(USER_KEY, 1);

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void getPrekeys_deviceNotFound() {
        when(deviceRepository.findById(new UserDeviceId(USER_KEY, 1)))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.getPrekeys(USER_KEY, 1));
    }

    /* -------------------------------------------------
     * GET AVAILABLE COUNT
     * ------------------------------------------------- */

    @Test
    void getAvailablePrekeysCount_success() {
        when(deviceRepository.findById(new UserDeviceId(USER_KEY, 1)))
                .thenReturn(Optional.of(mock()));
        when(repository.countByUserKeyAndDeviceIdAndUsedFalse(USER_KEY, 1))
                .thenReturn(5L);

        Long count = service.getAvailablePrekeysCount(USER_KEY, 1);

        assertEquals(5L, count);
    }

    @Test
    void getAvailablePrekeysCount_deviceNotFound() {
        when(deviceRepository.findById(new UserDeviceId(USER_KEY, 1)))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.getAvailablePrekeysCount(USER_KEY, 1));
    }

    /* -------------------------------------------------
     * DELETE
     * ------------------------------------------------- */

    @Test
    void delete_success() {
        when(repository.findByUserKeyAndDeviceId(USER_KEY, 1))
                .thenReturn(List.of(entity));

        doNothing().when(repository)
                .deleteByUserKeyAndDeviceId(USER_KEY, 1);

        service.delete(USER_KEY, 1);

        verify(repository)
                .deleteByUserKeyAndDeviceId(USER_KEY, 1);
    }

    @Test
    void delete_notFound() {
        when(repository.findByUserKeyAndDeviceId(USER_KEY, 1))
                .thenReturn(List.of());

        assertThrows(RecordNotFoundException.class,
                () -> service.delete(USER_KEY, 1));

        verify(repository, never())
                .deleteByUserKeyAndDeviceId(any(), anyInt());
    }
}
