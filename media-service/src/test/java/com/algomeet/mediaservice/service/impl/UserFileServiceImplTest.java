package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.FileAccessEntryService;

class UserFileServiceImplTest {

    @Mock
    private UserFileRepository repository;
    
    @Mock
    private FileAccessEntryService fileAccessEntryService;

    @Mock
    private UserStorageUsageService userStorageUsageService;

    @InjectMocks
    private UserFileServiceImpl service;

    private static final String FILE_ID = "33222222-2222-2222-2222-222222222222";
    private static final String OWNER = "22222222-2222-2222-2222-222222222222";
    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static UUID MESSAGE_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    /* =========================
       CREATE
       ========================= */

    @Test
    void create_setsDatesAndSaves() {
        UserFileDocument file = new UserFileDocument();

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserFileDocument saved = service.create(file);

        assertNotNull(saved.getDateCreated());
        assertNotNull(saved.getDateLastModified());
        verify(repository).save(file);
    }

    /* =========================
       GET FILE
       ========================= */

    @Test
    void getFile_success_ownerHasPermission() {
        UserFileDocument file = ownerFile();

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        UserFileDocument result = service.getFile(FILE_ID, OWNER, FilePermission.READ);

        assertEquals(file, result);
    }

    @Test
    void getFile_notFound() {
        when(repository.findById(FILE_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getFile(FILE_ID, OWNER, FilePermission.READ));
    }

    @Test
    void getFile_accessDenied() {
        UserFileDocument file = ownerFile();
        file.setOwner(OWNER);

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThrows(AccessDeniedException.class,
                () -> service.getFile(FILE_ID, USER, FilePermission.DELETE));
    }

    /* =========================
       LIST
       ========================= */

    @Test
    void listMyFiles() {
        when(repository.findByOwner(OWNER)).thenReturn(List.of(new UserFileDocument()));

        assertEquals(1, service.listMyFiles(OWNER).size());
    }
  
    /* =========================
       UPDATE LAST DOWNLOADED
       ========================= */

    @Test
    void updateLastDownloaded_updatesTimestamp() {
        UserFileDocument file = ownerFile();

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        service.updateLastRead(FILE_ID);

        assertNotNull(file.getDateLastRead());
        verify(repository).save(file);
    }

    /* =========================
       DELETE FILE
       ========================= */

    @Test
    void deleteFile_success() {
        UserFileDocument file = ownerFile();

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        service.deleteFile(FILE_ID, OWNER);

        verify(repository).deleteById(FILE_ID);
    }

    @Test
    void deleteFile_notOwner() {
        UserFileDocument file = ownerFile();

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThrows(AccessDeniedException.class,
                () -> service.deleteFile(FILE_ID, USER));
    }

    /* =========================
       HAS PERMISSION
       ========================= */

    @Test
    void hasPermission_ownerAlwaysTrue() {
        UserFileDocument file = ownerFile();

        assertTrue(service.hasPermission(file, OWNER, FilePermission.DELETE));
    }

    @Test
    void hasPermission_viaAcl() {
        UserFileDocument file = ownerFile();
        file.setOwner(OWNER);
        
        Set<FilePermission> permissions = new HashSet<>();
        permissions.add(FilePermission.READ);
        
        when(fileAccessEntryService.getPermissions(UUID.fromString(USER), UUID.fromString(file.getId()))).thenReturn(permissions);

        assertTrue(service.hasPermission(file, USER, FilePermission.READ));
        assertFalse(service.hasPermission(file, USER, FilePermission.DELETE));
    }

    /* =========================
       SHARE FILE
       ========================= */

    @Test
    void shareFile_success() {
        UserFileDocument file = ownerFile();
        when(repository.findAllById(Set.of(FILE_ID))).thenReturn(List.of(file));

        service.shareFile(Set.of(FILE_ID), OWNER, List.of(USER), UUID.randomUUID());

        assertEquals(null, file.getCleanupEligibleAt());
        
        verify(repository).saveAll(List.of(file));
    }


    @Test
    void shareFile_accessDenied() {
        UserFileDocument file = ownerFile();

        when(repository.findAllById(Set.of(FILE_ID))).thenReturn(List.of(file));

        assertThrows(AccessDeniedException.class,
                () -> service.shareFile(Set.of(FILE_ID), USER, List.of("00111111-1111-1111-1111-111111111111"), MESSAGE_ID));
    }

    /* =========================
       SOFT DELETE
       ========================= */

    @Test
    void softDelete_removesAclAndMarksForCleanup() {
        // "Delete for everyone" (owner + another participant) with no remaining access entries
        // → file must be marked for cleanup.
        UserFileDocument file = ownerFile();
        String otherUser = "00111111-1111-1111-1111-111111111111";

        when(repository.findAllById(Set.of(FILE_ID))).thenReturn(List.of(file));
        when(fileAccessEntryService.revokeAccess(any(), any(), any())).thenReturn(true);
        when(fileAccessEntryService.countByFileId(any())).thenReturn(0L);

        service.softDeleteAndMarkForCleanupIfOrphaned(
                Set.of(FILE_ID), OWNER, Set.of(OWNER, otherUser), MESSAGE_ID);

        assertNotNull(file.getCleanupEligibleAt());
        verify(repository).saveAll(List.of(file));
    }

    @Test
    void softDelete_shouldContinueProcessingAndRevokeOwnAccess_whenUserCannotDeleteForOthers() {
        UserFileDocument file = ownerFile();

        when(repository.findAllById(Set.of(FILE_ID))).thenReturn(List.of(file));
        
        // AccessDeniedException is no longer expected.
        // To support batch delete operations, processing should continue even when the
        // caller lacks permission to remove access for other users, ensuring that
        // the caller's own file access link can still be removed. This ensure that
        // all unused files clean up properly.
        service.softDeleteAndMarkForCleanupIfOrphaned(
        		Set.of(FILE_ID), USER, Set.of(USER), MESSAGE_ID);
                
        verify(fileAccessEntryService).revokeAccess(any(), any(), any());
    }

    /* =========================
       HELPERS
       ========================= */

    private UserFileDocument ownerFile() {
        UserFileDocument file = new UserFileDocument();
        file.setId(FILE_ID);
        file.setOwner(OWNER);
        file.setSize(1024L);
        return file;
    }
}
