package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.signalservice.dto.SessionBackupRequest;
import com.algomeet.signalservice.dto.SessionBackupResponse;
import com.algomeet.signalservice.entity.SessionBackup;
import com.algomeet.signalservice.entity.SessionBackupId;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.SessionBackupRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;

@ExtendWith(MockitoExtension.class)
class SessionBackupServiceTest {

    private static final UUID USER_KEY =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REMOTE_USER_KEY =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private SessionBackupRepository repository;

    @Mock
    private UserDeviceRepository deviceRepository;

    @InjectMocks
    private SessionBackupService service;

    private SessionBackup entity;

    @BeforeEach
    void setup() {
        SessionBackupId id = new SessionBackupId(
                USER_KEY,
                1,
                100,
                REMOTE_USER_KEY,
                2
        );

        entity = new SessionBackup();
        entity.setId(id);
        entity.setSerializedSession("QmFzZTY0RW5jb2RlZFNlc3Npb24=");
        entity.setAesAlg("AES/GCM/NoPadding");
        entity.setVersion("v1");
        entity.setSalt("U2FsdEJhc2U2NA==");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
    }

    /* -------------------------------------------------
     * SAVE BACKUP
     * ------------------------------------------------- */

    @Test
    void saveBackup_success() {
        SessionBackupRequest request = new SessionBackupRequest();
        request.setRegistrationId(100);
        request.setRemoteUserKey(REMOTE_USER_KEY);
        request.setRemoteDeviceId(2);
        request.setSerializedSession("QmFzZTY0RW5jb2RlZFNlc3Npb24=");
        request.setAesAlg("AES/GCM/NoPadding");
        request.setVersion("v1");
        request.setSalt("U2FsdEJhc2U2NA==");

        when(deviceRepository.findById(new UserDeviceId(USER_KEY, 1)))
                .thenReturn(Optional.of(mock()));

        when(repository.save(any(SessionBackup.class)))
                .thenReturn(entity);

        SessionBackupResponse response =
                service.saveBackup(USER_KEY, 1, request);

        assertNotNull(response);
        assertEquals(USER_KEY, response.getUserKey());
        assertEquals(1, response.getDeviceId());
        assertEquals(100, response.getRegistrationId());
        assertEquals(REMOTE_USER_KEY, response.getRemoteUserKey());
        assertEquals(2, response.getRemoteDeviceId());

        verify(repository).save(any(SessionBackup.class));
    }

    @Test
    void saveBackup_deviceNotFound() {
        SessionBackupRequest request = new SessionBackupRequest();
        request.setRegistrationId(100);
        request.setRemoteUserKey(REMOTE_USER_KEY);
        request.setRemoteDeviceId(2);

        when(deviceRepository.findById(new UserDeviceId(USER_KEY, 1)))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.saveBackup(USER_KEY, 1, request));

        verify(repository, never()).save(any());
    }

    /* -------------------------------------------------
     * RESTORE SESSIONS
     * ------------------------------------------------- */

    @Test
    void restoreSessions_success() {
        when(repository.findByIdUserKeyAndIdDeviceId(USER_KEY, 1))
                .thenReturn(List.of(entity));

        List<SessionBackupResponse> responses =
                service.restoreSessions(USER_KEY, 1);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(REMOTE_USER_KEY, responses.get(0).getRemoteUserKey());
    }

    @Test
    void restoreSessions_empty() {
        when(repository.findByIdUserKeyAndIdDeviceId(USER_KEY, 1))
                .thenReturn(List.of());

        List<SessionBackupResponse> responses =
                service.restoreSessions(USER_KEY, 1);

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    /* -------------------------------------------------
     * DELETE BY DEVICE + REGISTRATION + REMOTE USER
     * ------------------------------------------------- */

    @Test
    void deleteByDeviceRegistrationAndRemoteUser_success() {
        SessionBackupId id = entity.getId();

        when(repository.findById(id))
                .thenReturn(Optional.of(entity));

        doNothing().when(repository).deleteById(id);

        service.deleteByDeviceRegistrationAndRemoteUser(
                USER_KEY, 1, 100, REMOTE_USER_KEY, 2);

        verify(repository).deleteById(id);
    }

    @Test
    void deleteByDeviceRegistrationAndRemoteUser_notFound() {
        SessionBackupId id = entity.getId();

        when(repository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(RecordNotFoundException.class,
                () -> service.deleteByDeviceRegistrationAndRemoteUser(
                        USER_KEY, 1, 100, REMOTE_USER_KEY, 2));

        verify(repository, never()).deleteById(any());
    }

    /* -------------------------------------------------
     * DELETE BY DEVICE ID
     * ------------------------------------------------- */

    @Test
    void deleteByDeviceId_success() {
        when(repository.findByIdUserKeyAndIdDeviceId(USER_KEY, 1))
                .thenReturn(List.of(entity));

        doNothing().when(repository)
                .deleteByIdUserKeyAndIdDeviceId(USER_KEY, 1);

        service.deleteByDeviceId(USER_KEY, 1);

        verify(repository)
                .deleteByIdUserKeyAndIdDeviceId(USER_KEY, 1);
    }

    @Test
    void deleteByDeviceId_notFound() {
        when(repository.findByIdUserKeyAndIdDeviceId(USER_KEY, 1))
                .thenReturn(List.of());

        assertThrows(RecordNotFoundException.class,
                () -> service.deleteByDeviceId(USER_KEY, 1));

        verify(repository, never())
                .deleteByIdUserKeyAndIdDeviceId(any(), anyInt());
    }
}
