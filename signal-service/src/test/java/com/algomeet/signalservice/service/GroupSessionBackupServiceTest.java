package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.CollectionUtils;

import com.algomeet.signalservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalservice.entity.GroupSessionBackup;
import com.algomeet.signalservice.entity.GroupSessionBackupId;
import com.algomeet.signalservice.exceptions.GroupSessionBackupExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.GroupSessionBackupRepository;
import com.algomeet.signalservice.mapper.GroupSessionBackupMapper;

@ExtendWith(MockitoExtension.class)
class GroupSessionBackupServiceTest {

    @Mock
    private GroupSessionBackupRepository repository;

    @InjectMocks
    private GroupSessionBackupService service;

    private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DISTRIBUTION_ID = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.fromString("11111111-1111-1111-2222-111111111111");

    private GroupSessionBackupRequest request;

    @BeforeEach
    void setup() {
        request = new GroupSessionBackupRequest();
        request.setGroupId(GROUP_ID);
        request.setDistributionId(DISTRIBUTION_ID);
        request.setInbound(true);
        request.setDeviceId(1);
        request.setSerializedSession("Base64Session==");
    }

    /* -------------------------------------------------
     * SAVE BACKUP
     * ------------------------------------------------- */

    @Test
    void saveBackup_success() {
        GroupSessionBackup entity = GroupSessionBackupMapper.toEntity(USER_KEY, request);

        when(repository.findById(any(GroupSessionBackupId.class))).thenReturn(Optional.empty());
        when(repository.save(any(GroupSessionBackup.class))).thenReturn(entity);

        GroupSessionBackupResponse response = service.saveBackup(USER_KEY, request);

        assertNotNull(response);
        assertEquals(GROUP_ID, response.getGroupId());
        assertEquals(DISTRIBUTION_ID, response.getDistributionId());
        assertEquals(request.isInbound(), response.isInbound());
    }

    @Test
    void saveBackup_inboundAlreadyExists() {
        GroupSessionBackup entity = GroupSessionBackupMapper.toEntity(USER_KEY, request);

        when(repository.findById(any(GroupSessionBackupId.class))).thenReturn(Optional.of(entity));

        assertThrows(
                GroupSessionBackupExistsException.class,
                () -> service.saveBackup(USER_KEY, request)
        );
    }

    /* -------------------------------------------------
     * FIND BACKUPS
     * ------------------------------------------------- */

    @Test
    void findBackups_success() {
        GroupSessionBackup entity = GroupSessionBackupMapper.toEntity(USER_KEY, request);

        when(repository.findByIdUserKey(USER_KEY)).thenReturn(List.of(entity));

        List<GroupSessionBackupResponse> backups = service.findBackups(USER_KEY);

        assertEquals(1, backups.size());
        assertEquals(GROUP_ID, backups.get(0).getGroupId());
    }

    @Test
    void findBackup_success() {
        GroupSessionBackup entity = GroupSessionBackupMapper.toEntity(USER_KEY, request);

        when(repository.findById(any(GroupSessionBackupId.class))).thenReturn(Optional.of(entity));

        GroupSessionBackupResponse response = service.findBackup(USER_KEY, GROUP_ID, DISTRIBUTION_ID, true);

        assertNotNull(response);
        assertEquals(GROUP_ID, response.getGroupId());
        assertEquals(DISTRIBUTION_ID, response.getDistributionId());
    }

    @Test
    void findBackup_notFound() {
        when(repository.findById(any(GroupSessionBackupId.class))).thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.findBackup(USER_KEY, GROUP_ID, DISTRIBUTION_ID, true)
        );
    }

    @Test
    void findBackupByDevice_success() {
        GroupSessionBackup entity = GroupSessionBackupMapper.toEntity(USER_KEY, request);

        when(repository.findByIdUserKeyAndDeviceId(USER_KEY, 1)).thenReturn(List.of(entity));

        List<GroupSessionBackupResponse> backups = service.findBackupByDevice(USER_KEY, 1);

        assertEquals(1, backups.size());
        assertEquals(GROUP_ID, backups.get(0).getGroupId());
    }

    /* -------------------------------------------------
     * DELETE BACKUP
     * ------------------------------------------------- */

    @Test
    void deleteBackup_success() {
        GroupSessionBackup entity = GroupSessionBackupMapper.toEntity(USER_KEY, request);

        when(repository.findById(any(GroupSessionBackupId.class))).thenReturn(Optional.of(entity));
        doNothing().when(repository).deleteById(any(GroupSessionBackupId.class));

        service.deleteBackup(USER_KEY, GROUP_ID, DISTRIBUTION_ID, true);

        verify(repository).deleteById(any(GroupSessionBackupId.class));
    }

    @Test
    void deleteBackup_notFound() {
        when(repository.findById(any(GroupSessionBackupId.class))).thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.deleteBackup(USER_KEY, GROUP_ID, DISTRIBUTION_ID, true)
        );
    }

    @Test
    void deleteBackupByDevice_success() {
        GroupSessionBackup entity = GroupSessionBackupMapper.toEntity(USER_KEY, request);

        when(repository.findByIdUserKeyAndDeviceId(USER_KEY, 1)).thenReturn(List.of(entity));
        doNothing().when(repository).deleteByIdUserKeyAndDeviceId(USER_KEY, 1);

        service.deleteBackupByDevice(USER_KEY, 1);

        verify(repository).deleteByIdUserKeyAndDeviceId(USER_KEY, 1);
    }

    @Test
    void deleteBackupByDevice_notFound() {
        when(repository.findByIdUserKeyAndDeviceId(USER_KEY, 1)).thenReturn(List.of());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.deleteBackupByDevice(USER_KEY, 1)
        );
    }

    @Test
    void deleteAllUserBackups_success() {
        GroupSessionBackup entity = GroupSessionBackupMapper.toEntity(USER_KEY, request);

        when(repository.findByIdUserKey(USER_KEY)).thenReturn(List.of(entity));
        doNothing().when(repository).deleteByIdUserKey(USER_KEY);

        service.deleteAllUserBackups(USER_KEY);

        verify(repository).deleteByIdUserKey(USER_KEY);
    }

    @Test
    void deleteAllUserBackups_notFound() {
        when(repository.findByIdUserKey(USER_KEY)).thenReturn(List.of());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.deleteAllUserBackups(USER_KEY)
        );
    }
}
