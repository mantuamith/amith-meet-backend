package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

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

import com.algomeet.signalservice.dto.DeviceKeyBackupRequest;
import com.algomeet.signalservice.dto.DeviceKeyBackupResponse;
import com.algomeet.signalservice.dto.DeviceKeyBackupUpdateRequest;
import com.algomeet.signalservice.entity.DeviceKeyBackup;
import com.algomeet.signalservice.entity.DeviceKeyBackupId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.DeviceKeyBackupRepository;

@ExtendWith(MockitoExtension.class)
class DeviceKeyBackupServiceTest {

    @Mock
    private DeviceKeyBackupRepository repository;

    @InjectMocks
    private DeviceKeyBackupService service;

    private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Integer DEVICE_ID = 1;

    private DeviceKeyBackupRequest saveRequest;
    private DeviceKeyBackupUpdateRequest updateRequest;

    @BeforeEach
    void setup() {
        saveRequest = new DeviceKeyBackupRequest();
        saveRequest.setDeviceId(DEVICE_ID);
        saveRequest.setSerializedIdentityKey("identityKey");
        saveRequest.setSerializedPreKeys(List.of("preKey1", "preKey2"));
        saveRequest.setSerializedSignedPreKey("signedPreKey");
        saveRequest.setSerializedKyberPreKey("kyberPreKey");
        saveRequest.setAesAlg("AES/GCM/NoPadding");
        //saveRequest.setVersion(1);
        saveRequest.setSalt("salt");

        updateRequest = new DeviceKeyBackupUpdateRequest();
        updateRequest.setSerializedIdentityKey("updatedIdentityKey");
        updateRequest.setSerializedPreKeys(List.of("updatedPreKey"));
        updateRequest.setSerializedSignedPreKey("updatedSignedPreKey");
        updateRequest.setSerializedKyberPreKey("updatedKyberPreKey");
        updateRequest.setAesAlg("AES/CBC/PKCS5Padding");
        //updateRequest.setVersion(2);
        updateRequest.setSalt("updatedSalt");
    }

    /* -------------------------------------------------
     * SAVE BACKUP
     * ------------------------------------------------- */
    @Test
    void saveBackup_success() {
        when(repository.save(any(DeviceKeyBackup.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceKeyBackupResponse response = service.saveBackup(USER_KEY, saveRequest);

        assertNotNull(response);
        assertEquals(USER_KEY, response.getUserKey());
        assertEquals(DEVICE_ID, response.getDeviceId());
        assertEquals(saveRequest.getSerializedIdentityKey(), response.getSerializedIdentityKey());
    }

    /* -------------------------------------------------
     * UPDATE BACKUP
     * ------------------------------------------------- */
    @Test
    void updateBackup_success() {
        DeviceKeyBackup existing = new DeviceKeyBackup();
        existing.setId(new DeviceKeyBackupId(USER_KEY, DEVICE_ID));
        existing.setCreatedAt(Instant.now());

        when(repository.findById(new DeviceKeyBackupId(USER_KEY, DEVICE_ID)))
                .thenReturn(Optional.of(existing));
        when(repository.save(existing))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeviceKeyBackupResponse response = service.updateBackup(USER_KEY, DEVICE_ID, updateRequest);

        assertNotNull(response);
        assertEquals(updateRequest.getSerializedIdentityKey(), response.getSerializedIdentityKey());
        assertEquals(updateRequest.getSerializedSignedPreKey(), response.getSerializedSignedPreKey());
        assertEquals(updateRequest.getVersion(), response.getVersion());
    }

    @Test
    void updateBackup_notFound() {
        when(repository.findById(new DeviceKeyBackupId(USER_KEY, DEVICE_ID)))
                .thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.updateBackup(USER_KEY, DEVICE_ID, updateRequest)
        );
    }

    /* -------------------------------------------------
     * RESTORE BACKUP
     * ------------------------------------------------- */
    @Test
    void restoreBackup_success() {
        DeviceKeyBackup existing = new DeviceKeyBackup();
        existing.setId(new DeviceKeyBackupId(USER_KEY, DEVICE_ID));

        when(repository.findById(new DeviceKeyBackupId(USER_KEY, DEVICE_ID)))
                .thenReturn(Optional.of(existing));

        Optional<DeviceKeyBackupResponse> response = service.restoreBackup(USER_KEY, DEVICE_ID);

        assertNotNull(response);
        assertEquals(true, response.isPresent());
        assertEquals(USER_KEY, response.get().getUserKey());
    }

    @Test
    void restoreBackup_notFound() {
        when(repository.findById(new DeviceKeyBackupId(USER_KEY, DEVICE_ID)))
                .thenReturn(Optional.empty());

        Optional<DeviceKeyBackupResponse> response = service.restoreBackup(USER_KEY, DEVICE_ID);

        assertEquals(false, response.isPresent());
    }

    /* -------------------------------------------------
     * DELETE BACKUP
     * ------------------------------------------------- */
    @Test
    void deleteBackup_success() {
        DeviceKeyBackup existing = new DeviceKeyBackup();
        existing.setId(new DeviceKeyBackupId(USER_KEY, DEVICE_ID));

        when(repository.findById(new DeviceKeyBackupId(USER_KEY, DEVICE_ID)))
                .thenReturn(Optional.of(existing));
        doNothing().when(repository).delete(existing);

        service.deleteBackup(USER_KEY, DEVICE_ID);
    }

    @Test
    void deleteBackup_notFound() {
        when(repository.findById(new DeviceKeyBackupId(USER_KEY, DEVICE_ID)))
                .thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.deleteBackup(USER_KEY, DEVICE_ID)
        );
    }

    @Test
    void deleteBackupByUser_success() {
        doNothing().when(repository).deleteByIdUserKey(USER_KEY);
        service.deleteBackup(USER_KEY);
    }
}
