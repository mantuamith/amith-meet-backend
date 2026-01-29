package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.algomeet.mediaservice.document.FileAccessEntry;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.repository.UserFileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

class UserFileServiceImplTest {

    @Mock
    private UserFileRepository repository;

    @InjectMocks
    private UserFileServiceImpl service;

    private static final String FILE_ID = "file1";
    private static final String OWNER = "owner";
    private static final String USER = "user1";

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

        UserFileDocument result = service.getFile(FILE_ID, OWNER, FilePermission.DOWNLOAD);

        assertEquals(file, result);
    }

    @Test
    void getFile_notFound() {
        when(repository.findById(FILE_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getFile(FILE_ID, OWNER, FilePermission.DOWNLOAD));
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

    @Test
    void listFilesSharedWithMe() {
        when(repository.findFilesUserHasAccessTo(USER)).thenReturn(List.of(new UserFileDocument()));

        assertEquals(1, service.listFilesSharedWithMe(USER).size());
    }

    /* =========================
       UPDATE LAST DOWNLOADED
       ========================= */

    @Test
    void updateLastDownloaded_updatesTimestamp() {
        UserFileDocument file = ownerFile();

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        service.updateLastDownloaded(FILE_ID);

        assertNotNull(file.getDateLastDownloaded());
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

        FileAccessEntry entry = new FileAccessEntry(
                USER, 1, Set.of(FilePermission.DOWNLOAD));

        file.getAccessControlList().add(entry);

        assertTrue(service.hasPermission(file, USER, FilePermission.DOWNLOAD));
        assertFalse(service.hasPermission(file, USER, FilePermission.DELETE));
    }

    /* =========================
       SHARE FILE
       ========================= */

    @Test
    void shareFile_success() {
        UserFileDocument file = ownerFile();
        file.getAccessControlList().add(ownerAcl());

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        service.shareFile(FILE_ID, OWNER, List.of(USER));

        assertEquals(2, file.getAccessControlList().size());
        verify(repository).save(file);
    }

    @Test
    void shareFile_accessDenied() {
        UserFileDocument file = ownerFile();

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThrows(AccessDeniedException.class,
                () -> service.shareFile(FILE_ID, USER, List.of("x")));
    }

    /* =========================
       SOFT DELETE
       ========================= */

    @Test
    void softDelete_removesAclAndMarksForCleanup() {
        UserFileDocument file = ownerFile();
        file.getAccessControlList().add(ownerAcl());

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        service.softDeleteAndMarkForCleanupIfOrphaned(FILE_ID, OWNER, null);

        assertTrue(file.getAccessControlList().isEmpty());
        assertNotNull(file.getCleanupEligibleAt());
        verify(repository).save(file);
    }

    @Test
    void softDelete_accessDenied() {
        UserFileDocument file = ownerFile();

        when(repository.findById(FILE_ID)).thenReturn(Optional.of(file));

        assertThrows(AccessDeniedException.class,
                () -> service.softDeleteAndMarkForCleanupIfOrphaned(FILE_ID, USER, null));
    }

    /* =========================
       HELPERS
       ========================= */

    private UserFileDocument ownerFile() {
        UserFileDocument file = new UserFileDocument();
        file.setId(FILE_ID);
        file.setOwner(OWNER);
        file.setAccessControlList(new ArrayList<>());
        return file;
    }

    private FileAccessEntry ownerAcl() {
        return new FileAccessEntry(
                OWNER,
                1,
                Set.of(FilePermission.DOWNLOAD,
                       FilePermission.SHARE,
                       FilePermission.VIEW,
                       FilePermission.DELETE)
        );
    }
}
