package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.algomeet.common.service.AbstractGroupCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.repository.FileAccessEntryRepository;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.FileAccessEntryService;
import com.algomeet.mediaservice.service.FileAccessPermission;
import com.algomeet.mediaservice.service.GroupFileAccessEntryService;
import com.algomeet.mediaservice.service.impl.FileAccessPermissionImpl;

@ExtendWith(MockitoExtension.class)
class UserFileServiceImplDeleteTest {

    @Mock private UserFileRepository repository;
    @Mock private UserStorageUsageService userStorageUsageService;
    @Mock private FileAccessEntryService fileAccessEntryService;
    @Mock private FileAccessEntryRepository fileAccessEntryRepository;
    @Mock private FileAccessPermission fileAccessPermission;
    @Mock private AbstractGroupCache groupCacheService;
    @Mock private GroupFileAccessEntryService groupFileAccessEntryService;

    @InjectMocks
    private UserFileServiceImpl service;

    private static final String SENDER_KEY   = UUID.randomUUID().toString();
    private static final String RECEIVER_KEY = UUID.randomUUID().toString();
    private static final UUID   MESSAGE_ID   = UUID.randomUUID();
    private static final UUID   GROUP_ID   = UUID.randomUUID();

    private UserFileDocument makeFile(String owner, String conversationId) {
        UserFileDocument f = new UserFileDocument();
        f.setId(UUID.randomUUID().toString());
        f.setOwner(owner);
        f.setConversationId(conversationId);
        f.setSize(1024L);
        f.setStorage("LOCAL");
        f.setUploadContext("CHAT");
        return f;
    }

    @Nested
    class DeleteForMe {

        @Test
        void deleteForMe_senderOnly_doesNotMarkCleanupEligible_whenShareNeverCalled() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(repository.findAllById(anyCollection())).thenReturn(List.of(file));
            when(fileAccessEntryService.revokeAccess(any(), any(), any())).thenReturn(false);

            service.softDeleteAndMarkForCleanupIfOrphaned(
                    Set.of(file.getId()), SENDER_KEY, Set.of(SENDER_KEY), GROUP_ID, MESSAGE_ID);

            verify(repository).saveAll(argThat(files -> {
                List<UserFileDocument> list = (List<UserFileDocument>) files;
                return list.isEmpty();
            }));
            assertNull(file.getCleanupEligibleAt(),
                    "File must not be marked for cleanup on 'delete for me'");
        }

        @Test
        void deleteForMe_senderOnly_doesNotMarkCleanup_whenReceiverEntryExists() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(repository.findAllById(anyCollection())).thenReturn(List.of(file));
            when(fileAccessEntryService.revokeAccess(eq(UUID.fromString(SENDER_KEY)), any(), eq(MESSAGE_ID)))
                    .thenReturn(true);

            service.softDeleteAndMarkForCleanupIfOrphaned(
                    Set.of(file.getId()), SENDER_KEY, Set.of(SENDER_KEY), GROUP_ID, MESSAGE_ID);

            assertNull(file.getCleanupEligibleAt(),
                    "File must not be marked for cleanup while receiver entry still exists");
        }

        @Test
        void deleteForMe_receiverOnly_doesNotMarkCleanup_whenSenderEntryExists() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(repository.findAllById(anyCollection())).thenReturn(List.of(file));
            when(fileAccessPermission.hasPermission(any(UserFileDocument.class), eq(SENDER_KEY), eq(FilePermission.DELETE))).thenReturn(true);
            when(fileAccessEntryService.revokeAccess(eq(UUID.fromString(RECEIVER_KEY)), any(), eq(MESSAGE_ID)))
                    .thenReturn(true);
            when(fileAccessEntryService.countByFileId(any())).thenReturn(1L);

            service.softDeleteAndMarkForCleanupIfOrphaned(
                    Set.of(file.getId()), SENDER_KEY, Set.of(RECEIVER_KEY), GROUP_ID, MESSAGE_ID);

            assertNull(file.getCleanupEligibleAt());
        }
    }

    @Nested
    class DeleteForEveryone {

        @Test
        void deleteForEveryone_marksCleanupEligible_whenAllEntriesRevoked() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(repository.findAllById(anyCollection())).thenReturn(List.of(file));
            when(fileAccessPermission.hasPermission(any(UserFileDocument.class), eq(SENDER_KEY), eq(FilePermission.DELETE))).thenReturn(true);
            when(fileAccessEntryService.revokeAccess(any(), any(), any())).thenReturn(true);
            when(fileAccessEntryService.countByFileId(any())).thenReturn(0L);

            service.softDeleteAndMarkForCleanupIfOrphaned(
                    Set.of(file.getId()),
                    SENDER_KEY,
                    Set.of(SENDER_KEY, RECEIVER_KEY),
                    GROUP_ID,
                    MESSAGE_ID);

            assertNotNull(file.getCleanupEligibleAt(),
                    "File MUST be marked for cleanup when all participants delete");
            assertFalse(file.getCleanupEligibleAt().isAfter(Instant.now()),
                    "cleanupEligibleAt should be now or in the past");
        }

        @Test
        void deleteForEveryone_doesNotMarkCleanup_whenEntriesStillExist() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(repository.findAllById(anyCollection())).thenReturn(List.of(file));
            when(fileAccessPermission.hasPermission(any(UserFileDocument.class), eq(SENDER_KEY), eq(FilePermission.DELETE))).thenReturn(true);
            when(fileAccessEntryService.revokeAccess(any(), any(), any())).thenReturn(true);
            when(fileAccessEntryService.countByFileId(any())).thenReturn(2L);

            service.softDeleteAndMarkForCleanupIfOrphaned(
                    Set.of(file.getId()),
                    SENDER_KEY,
                    Set.of(SENDER_KEY, RECEIVER_KEY),
                    GROUP_ID,
                    MESSAGE_ID);

            assertNull(file.getCleanupEligibleAt());
        }
    }

    @Nested
    class HasPermissionConversationIdBypass {

        private FileAccessPermissionImpl realPermission;

        @BeforeEach
        void setUp() {
            realPermission = new FileAccessPermissionImpl(fileAccessEntryService, groupCacheService, groupFileAccessEntryService);
        }

        @Test
        void hasPermission_conversationFile_grantedWhenAccessEntryExists() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(fileAccessEntryService.hasAccess(
                    eq(UUID.fromString(RECEIVER_KEY)), eq(UUID.fromString(file.getId()))))
                    .thenReturn(true);

            FileAccessPermission localPerm = new FileAccessPermissionImpl(fileAccessEntryService, null, null);
            assertTrue(localPerm.hasPermission(file, RECEIVER_KEY, FilePermission.READ));
            assertTrue(realPermission.hasPermission(file, RECEIVER_KEY, FilePermission.READ),
                    "Receiver with FileAccessEntry must be granted READ");
        }

        @Test
        void hasPermission_conversationFile_deniedWhenNoAccessEntry() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            String strangerKey = UUID.randomUUID().toString();

            FileAccessPermission localPerm = new FileAccessPermissionImpl(fileAccessEntryService, null, null);
            assertFalse(localPerm.hasPermission(file, strangerKey, FilePermission.READ));
            assertFalse(realPermission.hasPermission(file, strangerKey, FilePermission.READ),
                    "User with no FileAccessEntry must be denied even when conversationId is set");
        }

        @Test
        void hasPermission_conversationFile_nullConversationId_fallsThrough() {
            UserFileDocument file = makeFile(SENDER_KEY, null);
            String receiverKey = RECEIVER_KEY;
            when(fileAccessEntryService.getPermissions(
                    eq(UUID.fromString(receiverKey)), eq(UUID.fromString(file.getId()))))
                    .thenReturn(Set.of(FilePermission.READ));
            FileAccessPermission localPerm = new FileAccessPermissionImpl(fileAccessEntryService, null, null);
            assertTrue(localPerm.hasPermission(file, receiverKey, FilePermission.READ));
            assertTrue(realPermission.hasPermission(file, receiverKey, FilePermission.READ));
        }

        @Test
        void hasPermission_expiredFile_deniedEvenIfAccessEntryExists() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            file.setCleanupEligibleAt(Instant.now().minusSeconds(60));

            assertFalse(realPermission.hasPermission(file, RECEIVER_KEY, FilePermission.READ),
                    "Expired files must be denied regardless of access entries");
            verifyNoInteractions(fileAccessEntryService);
        }

        @Test
        void hasPermission_owner_alwaysGranted() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            assertTrue(realPermission.hasPermission(file, SENDER_KEY, FilePermission.READ));
            assertTrue(realPermission.hasPermission(file, SENDER_KEY, FilePermission.DELETE));
            assertTrue(realPermission.hasPermission(file, SENDER_KEY, FilePermission.SHARE));
        }
    }
}
