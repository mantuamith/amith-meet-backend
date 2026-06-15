package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.algomeet.common.service.GroupCacheService;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.UserFileDocument;
import com.algomeet.mediaservice.repository.FileAccessEntryRepository;
import com.algomeet.mediaservice.repository.UserFileRepository;
import com.algomeet.mediaservice.service.FileAccessEntryService;

/**
 * Tests for the three bugs fixed in UserFileServiceImpl:
 *
 *  Bug 1 – "delete for me" must NOT mark cleanupEligibleAt when countByFileId == 0
 *           (i.e. shareFile() never ran), because recipients still rely on the
 *           conversationId / FileAccessEntry for access.
 *
 *  Bug 2 – InternalFileController.batchDelete was passing SecurityUtil.getUserKey()
 *           instead of the @RequestParam userKey — already fixed at controller level;
 *           the service-layer effect (canDeleteForOthers evaluated with wrong key) is
 *           verified here indirectly via the correct-key path.
 *
 *  Bug 3 – conversationId bypass in hasPermission() must require an active
 *           FileAccessEntry instead of granting READ to any authenticated user.
 */
@ExtendWith(MockitoExtension.class)
class UserFileServiceImplDeleteTest {

    @Mock private UserFileRepository repository;
    @Mock private UserStorageUsageService userStorageUsageService;
    @Mock private FileAccessEntryService fileAccessEntryService;
    @Mock private FileAccessEntryRepository fileAccessEntryRepository;
    @Mock private GroupCacheService groupCacheService;

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

    // =========================================================================
    // Bug 1 — "delete for me" must not mark the file for cleanup prematurely
    // =========================================================================

    @Nested
    class DeleteForMe {

        @Test
        void deleteForMe_senderOnly_doesNotMarkCleanupEligible_whenShareNeverCalled() {
            // File owner = sender; shareFile() was never called → 0 access entries
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(repository.findAllById(anyCollection())).thenReturn(List.of(file));
            when(fileAccessEntryService.revokeAccess(any(), any(), any())).thenReturn(false);
            // Note: countByFileId is NOT called — isDeletingForEveryone=false short-circuits the cleanup block

            // Sender deletes "for me" — only their own key in deleteWithUserKeys
            service.softDeleteAndMarkForCleanupIfOrphaned(
                    Set.of(file.getId()), SENDER_KEY, Set.of(SENDER_KEY), GROUP_ID, MESSAGE_ID);

            // cleanupEligibleAt must NOT be set — receiver still has implicit access
            verify(repository).saveAll(argThat(files -> {
                List<UserFileDocument> list = (List<UserFileDocument>) files;
                return list.isEmpty(); // no modified files saved
            }));
            assertNull(file.getCleanupEligibleAt(),
                    "File must not be marked for cleanup on 'delete for me'");
        }

        @Test
        void deleteForMe_senderOnly_doesNotMarkCleanup_whenReceiverEntryExists() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(repository.findAllById(anyCollection())).thenReturn(List.of(file));
            // Sender's revoke succeeds; receiver's entry still in DB
            // countByFileId NOT stubbed — isDeletingForEveryone=false so cleanup block is skipped
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
            // Receiver deletes for themselves; sender's entry remains → count = 1
            when(fileAccessEntryService.revokeAccess(eq(UUID.fromString(RECEIVER_KEY)), any(), eq(MESSAGE_ID)))
                    .thenReturn(true);
            when(fileAccessEntryService.countByFileId(any())).thenReturn(1L);

            service.softDeleteAndMarkForCleanupIfOrphaned(
                    Set.of(file.getId()), SENDER_KEY, Set.of(RECEIVER_KEY), GROUP_ID, MESSAGE_ID);

            assertNull(file.getCleanupEligibleAt());
        }
    }

    // =========================================================================
    // Bug 1 (positive) — "delete for everyone" SHOULD mark cleanup when orphaned
    // =========================================================================

    @Nested
    class DeleteForEveryone {

        @Test
        void deleteForEveryone_marksCleanupEligible_whenAllEntriesRevoked() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(repository.findAllById(anyCollection())).thenReturn(List.of(file));

            // Both sender's and receiver's access entries are revoked
            when(fileAccessEntryService.revokeAccess(any(), any(), any())).thenReturn(true);
            // After both revocations, no entries left
            when(fileAccessEntryService.countByFileId(any())).thenReturn(0L);

            // Also need hasPermission for canDeleteForOthers → sender is owner → true
            // (no extra stub needed; owner check uses file.getOwner())

            service.softDeleteAndMarkForCleanupIfOrphaned(
                    Set.of(file.getId()),
                    SENDER_KEY,
                    Set.of(SENDER_KEY, RECEIVER_KEY),   // ← both users = delete for everyone
                    GROUP_ID,
                    MESSAGE_ID);

            assertNotNull(file.getCleanupEligibleAt(),
                    "File MUST be marked for cleanup when all participants delete");
            assertFalse(file.getCleanupEligibleAt().isAfter(Instant.now()),
                    "cleanupEligibleAt should be now or in the past");
        }

        @Test
        void deleteForEveryone_doesNotMarkCleanup_whenEntriesStillExist() {
            // Revocations succeed but countByFileId still > 0 (e.g. group chat with more members)
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            when(repository.findAllById(anyCollection())).thenReturn(List.of(file));
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

    // =========================================================================
    // Bug 3 — hasPermission: conversationId bypass requires a FileAccessEntry
    // =========================================================================

    @Nested
    class HasPermissionConversationIdBypass {

        @Test
        void hasPermission_conversationFile_grantedWhenAccessEntryExists() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            // Receiver has an active FileAccessEntry
            when(fileAccessEntryService.hasAccess(
                    eq(UUID.fromString(RECEIVER_KEY)), eq(UUID.fromString(file.getId()))))
                    .thenReturn(true);

            assertTrue(service.hasPermission(file, RECEIVER_KEY, FilePermission.READ),
                    "Receiver with FileAccessEntry must be granted READ");
        }

        @Test
        void hasPermission_conversationFile_deniedWhenNoAccessEntry() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            // Stranger has NO FileAccessEntry — hasAccess returns false
            // getPermissions NOT stubbed: conversationId check returns false immediately,
            // so check 5 (getPermissions) is never reached
            String strangerKey = UUID.randomUUID().toString();
            when(fileAccessEntryService.hasAccess(
                    eq(UUID.fromString(strangerKey)), eq(UUID.fromString(file.getId()))))
                    .thenReturn(false);

            assertFalse(service.hasPermission(file, strangerKey, FilePermission.READ),
                    "User with no FileAccessEntry must be denied even when conversationId is set");
        }

        @Test
        void hasPermission_conversationFile_nullConversationId_fallsThrough() {
            // No conversationId → bypass does not apply → falls through to FileAccessEntry check
            UserFileDocument file = makeFile(SENDER_KEY, null);
            String receiverKey = RECEIVER_KEY;
            when(fileAccessEntryService.getPermissions(
                    eq(UUID.fromString(receiverKey)), eq(UUID.fromString(file.getId()))))
                    .thenReturn(Set.of(FilePermission.READ));

            assertTrue(service.hasPermission(file, receiverKey, FilePermission.READ));
        }

        @Test
        void hasPermission_expiredFile_deniedEvenIfAccessEntryExists() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            file.setCleanupEligibleAt(Instant.now().minusSeconds(60)); // expired

            assertFalse(service.hasPermission(file, RECEIVER_KEY, FilePermission.READ),
                    "Expired files must be denied regardless of access entries");

            // hasAccess must NOT be called — isExpired check short-circuits
            verifyNoInteractions(fileAccessEntryService);
        }

        @Test
        void hasPermission_owner_alwaysGranted() {
            UserFileDocument file = makeFile(SENDER_KEY, "conv-001");
            // Owner should not need FileAccessEntry
            assertTrue(service.hasPermission(file, SENDER_KEY, FilePermission.READ));
            assertTrue(service.hasPermission(file, SENDER_KEY, FilePermission.DELETE));
            assertTrue(service.hasPermission(file, SENDER_KEY, FilePermission.SHARE));
        }
    }
}
