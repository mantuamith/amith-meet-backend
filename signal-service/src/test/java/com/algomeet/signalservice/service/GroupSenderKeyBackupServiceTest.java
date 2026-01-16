package com.algomeet.signalservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
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

import com.algomeet.signalservice.dto.GroupSenderKeyBackupRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupUpdateRequest;
import com.algomeet.signalservice.entity.GroupSenderKeyBackup;
import com.algomeet.signalservice.entity.GroupSenderKeyBackupId;
import com.algomeet.signalservice.exceptions.GroupSenderKeyBackupExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.GroupSenderKeyBackupRepository;

@ExtendWith(MockitoExtension.class)
class GroupSenderKeyBackupServiceTest {

    @Mock
    private GroupSenderKeyBackupRepository repository;

    @InjectMocks
    private GroupSenderKeyBackupService service;

    private static final UUID USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DISTRIBUTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String GROUP_ID = "group-1";

    private GroupSenderKeyBackupRequest saveRequest;
    private GroupSenderKeyBackupUpdateRequest updateRequest;

    @BeforeEach
    void setup() {
        saveRequest = new GroupSenderKeyBackupRequest();
        saveRequest.setGroupId(GROUP_ID);
        saveRequest.setDistributionId(DISTRIBUTION_ID);
        saveRequest.setSerializedSkdm("serializedData");
        saveRequest.setAesAlg("AES/GCM/NoPadding");
        saveRequest.setSalt("saltValue");
        //saveRequest.setVersion(1);

        updateRequest = new GroupSenderKeyBackupUpdateRequest();
        updateRequest.setSerializedSkdm("updatedData");
        updateRequest.setAesAlg("AES/CBC/PKCS5Padding");
        updateRequest.setSalt("updatedSalt");
        //updateRequest.setVersion(2);
    }

    /* -------------------------------------------------
     * SAVE
     * ------------------------------------------------- */
    @Test
    void save_success() {
    	GroupSenderKeyBackup groupSenderKeyBackup = new GroupSenderKeyBackup();
    	groupSenderKeyBackup.setId(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID));
    	
        when(repository.findById(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID)))
                .thenReturn(Optional.empty());
        when(repository.save(any(GroupSenderKeyBackup.class)))
                .thenReturn(groupSenderKeyBackup);

        GroupSenderKeyBackupResponse response = service.save(USER_KEY, saveRequest);

        assertNotNull(response);
    }

    @Test
    void save_alreadyExists() {
        when(repository.findById(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID)))
                .thenReturn(Optional.of(new GroupSenderKeyBackup()));

        assertThrows(
                GroupSenderKeyBackupExistsException.class,
                () -> service.save(USER_KEY, saveRequest)
        );
    }

    /* -------------------------------------------------
     * UPDATE
     * ------------------------------------------------- */
    @Test
    void update_success() {
    	
    	GroupSenderKeyBackup entity = new GroupSenderKeyBackup();
    	entity.setId(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID));
    	
        when(repository.findById(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID)))
                .thenReturn(Optional.of(entity));
        when(repository.save(entity))
                .thenReturn(entity);

        GroupSenderKeyBackupResponse response = service.update(USER_KEY, GROUP_ID, DISTRIBUTION_ID, updateRequest);

        assertNotNull(response);
        assertEquals(updateRequest.getSerializedSkdm(), entity.getSerializedSkdm());
        assertEquals(updateRequest.getAesAlg(), entity.getAesAlg());
        assertEquals(updateRequest.getSalt(), entity.getSalt());
        assertEquals(updateRequest.getVersion(), entity.getVersion());
    }

    @Test
    void update_notFound() {
        when(repository.findById(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID)))
                .thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.update(USER_KEY, GROUP_ID, DISTRIBUTION_ID, updateRequest)
        );
    }

    /* -------------------------------------------------
     * FIND BY ID
     * ------------------------------------------------- */
    @Test
    void findById_success() {
    	GroupSenderKeyBackup groupSenderKeyBackup = new GroupSenderKeyBackup();
    	groupSenderKeyBackup.setId(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID));
    	
        when(repository.findById(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID)))
                .thenReturn(Optional.of(groupSenderKeyBackup));

        Optional<GroupSenderKeyBackupResponse> response = service.findById(USER_KEY, GROUP_ID, DISTRIBUTION_ID);

        assertNotNull(response);
        assertEquals(true, response.isPresent());
    }

    @Test
    void findById_notFound() {
        when(repository.findById(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID)))
                .thenReturn(Optional.empty());

        Optional<GroupSenderKeyBackupResponse> response = service.findById(USER_KEY, GROUP_ID, DISTRIBUTION_ID);

        assertEquals(false, response.isPresent());
    }

    /* -------------------------------------------------
     * FIND BY USER
     * ------------------------------------------------- */
    @Test
    void findByUser_success() {
    	GroupSenderKeyBackup entity = new GroupSenderKeyBackup();
    	entity.setId(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID));
    	
        when(repository.findByIdUserKey(USER_KEY))
                .thenReturn(List.of(entity));

        List<GroupSenderKeyBackupResponse> result = service.findByUser(USER_KEY);

        assertEquals(1, result.size());
    }

    /* -------------------------------------------------
     * FIND BY GROUP
     * ------------------------------------------------- */
    @Test
    void findByGroup_success() {
    	GroupSenderKeyBackup groupSenderKeyBackup = new GroupSenderKeyBackup();
    	groupSenderKeyBackup.setId(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID));
    	
        when(repository.findByIdGroupId(GROUP_ID))
                .thenReturn(List.of(groupSenderKeyBackup));

        List<GroupSenderKeyBackupResponse> result = service.findByGroup(GROUP_ID);

        assertEquals(1, result.size());
    }

    /* -------------------------------------------------
     * DELETE
     * ------------------------------------------------- */
    @Test
    void delete_success() {
        when(repository.findById(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID)))
                .thenReturn(Optional.of(new GroupSenderKeyBackup()));
        doNothing().when(repository).deleteById(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID));

        service.delete(USER_KEY, GROUP_ID, DISTRIBUTION_ID);
    }

    @Test
    void delete_notFound() {
        when(repository.findById(new GroupSenderKeyBackupId(USER_KEY, GROUP_ID, DISTRIBUTION_ID)))
                .thenReturn(Optional.empty());

        assertThrows(
                RecordNotFoundException.class,
                () -> service.delete(USER_KEY, GROUP_ID, DISTRIBUTION_ID)
        );
    }
}
