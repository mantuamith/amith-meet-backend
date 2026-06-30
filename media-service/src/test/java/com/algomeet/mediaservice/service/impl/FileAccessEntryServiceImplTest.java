package com.algomeet.mediaservice.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bson.BsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.algomeet.mediaservice.document.FileAccessEntryDocument;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.repository.FileAccessEntryRepository;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

@ExtendWith(MockitoExtension.class)
class FileAccessEntryServiceImplTest {

    @Mock
    private FileAccessEntryRepository repository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private FileAccessEntryServiceImpl service;

    private UUID userKey;
    private UUID fileId;
    private UUID messageId;
    private String compositeId;
    private Set<FilePermission> permissions;

    @BeforeEach
    void setUp() {
        userKey = UUID.randomUUID();
        fileId = UUID.randomUUID();
        messageId = UUID.randomUUID();
        compositeId = userKey.toString() + "_" + fileId.toString();
        permissions = Set.of(FilePermission.READ, FilePermission.DELETE);
    }

    @Nested
    class GrantAccessTests {

        @Test
        void grantAccess_alreadyProcessedMessageId_returnsFalse() {
            FileAccessEntryDocument document = new FileAccessEntryDocument();
            document.setReferencingMessageIds(Set.of(messageId));

            when(repository.findById(compositeId)).thenReturn(Optional.of(document));

            boolean result = service.grantAccess(userKey, fileId, permissions, messageId);

            assertFalse(result);
            verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(FileAccessEntryDocument.class));
        }

        @Test
        void grantAccess_brandNewDocument_performsUpsertSuccessfully() {
            when(repository.findById(compositeId)).thenReturn(Optional.empty());
            
            UpdateResult updateResult = UpdateResult.acknowledged(0L, 1L, new BsonString(compositeId));
            ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

            when(mongoTemplate.upsert(queryCaptor.capture(), updateCaptor.capture(), eq(FileAccessEntryDocument.class)))
                    .thenReturn(updateResult);

            boolean result = service.grantAccess(userKey, fileId, permissions, messageId);

            assertTrue(result);
            
            // Verify query target criteria
            Query executedQuery = queryCaptor.getValue();
            assertEquals(compositeId, executedQuery.getQueryObject().get("_id"));

            // Verify update instructions include adding the message ID
            Update executedUpdate = updateCaptor.getValue();
            assertTrue(executedUpdate.getUpdateObject().containsKey("$addToSet"));
        }

        @Test
        void grantAccess_existingDocWithoutMessageId_updatesDocument() {
            FileAccessEntryDocument document = new FileAccessEntryDocument();
            document.setReferencingMessageIds(Set.of(UUID.randomUUID())); // different messageId

            when(repository.findById(compositeId)).thenReturn(Optional.of(document));
            
            UpdateResult updateResult = UpdateResult.acknowledged(1L, 1L, null);
            when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(FileAccessEntryDocument.class)))
                    .thenReturn(updateResult);

            boolean result = service.grantAccess(userKey, fileId, permissions, messageId);

            assertTrue(result);
        }

        @Test
        void grantAccess_upsertNoOp_returnsFalse() {
            when(repository.findById(compositeId)).thenReturn(Optional.empty());
            
            UpdateResult updateResult = UpdateResult.acknowledged(0L, 0L, null);
            when(mongoTemplate.upsert(any(Query.class), any(Update.class), eq(FileAccessEntryDocument.class)))
                    .thenReturn(updateResult);

            boolean result = service.grantAccess(userKey, fileId, permissions, messageId);

            assertFalse(result);
        }
    }

    @Nested
    class RevokeAccessTests {

        @Test
        void revokeAccess_documentNotFoundOrMessageIdMissing_returnsFalse() {
            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(FileAccessEntryDocument.class)))
                    .thenReturn(null);

            boolean result = service.revokeAccess(userKey, fileId, messageId);

            assertFalse(result);
        }

        @Test
        void revokeAccess_docStillHasOtherReferences_doesNotDeleteDocument() {
            FileAccessEntryDocument updatedDoc = new FileAccessEntryDocument();
            updatedDoc.setReferencingMessageIds(Set.of(UUID.randomUUID())); // still has an active reference

            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(FileAccessEntryDocument.class)))
                    .thenReturn(updatedDoc);

            boolean result = service.revokeAccess(userKey, fileId, messageId);

            assertTrue(result);
            verify(mongoTemplate, never()).remove(any(Query.class), eq(FileAccessEntryDocument.class));
        }

        @Test
        void revokeAccess_referencesDropToZero_deletesDocumentSuccessfully() {
            FileAccessEntryDocument updatedDoc = new FileAccessEntryDocument();
            updatedDoc.setReferencingMessageIds(Collections.emptySet()); // no references left

            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(FileAccessEntryDocument.class)))
                    .thenReturn(updatedDoc);
            when(mongoTemplate.remove(any(Query.class), eq(FileAccessEntryDocument.class)))
                    .thenReturn(DeleteResult.acknowledged(1L));

            boolean result = service.revokeAccess(userKey, fileId, messageId);

            assertTrue(result);
            verify(mongoTemplate).remove(any(Query.class), eq(FileAccessEntryDocument.class));
        }

        @Test
        void revokeAccess_concurrentReShareDetected_skipsPurgeDeletion() {
            FileAccessEntryDocument updatedDoc = new FileAccessEntryDocument();
            updatedDoc.setReferencingMessageIds(Collections.emptySet());

            when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(FileAccessEntryDocument.class)))
                    .thenReturn(updatedDoc);
            // Simulated race condition: delete count is 0 because references were modified concurrently
            when(mongoTemplate.remove(any(Query.class), eq(FileAccessEntryDocument.class)))
                    .thenReturn(DeleteResult.acknowledged(0L));

            boolean result = service.revokeAccess(userKey, fileId, messageId);

            assertTrue(result);
            verify(mongoTemplate).remove(any(Query.class), eq(FileAccessEntryDocument.class));
        }
    }

    @Nested
    class BasicQueriesAndLifecycleTests {

        @Test
        void hasAccess_exists_returnsTrue() {
            when(repository.existsById(compositeId)).thenReturn(true);
            assertTrue(service.hasAccess(userKey, fileId));
        }

        @Test
        void hasAccess_notExists_returnsFalse() {
            when(repository.existsById(compositeId)).thenReturn(false);
            assertFalse(service.hasAccess(userKey, fileId));
        }

        @Test
        void getPermissions_found_returnsPermissionSet() {
            FileAccessEntryDocument document = new FileAccessEntryDocument();
            document.setPermissions(permissions);

            when(repository.findById(compositeId)).thenReturn(Optional.of(document));

            Set<FilePermission> actual = service.getPermissions(userKey, fileId);
            assertEquals(permissions, actual);
        }

        @Test
        void getPermissions_notFound_returnsEmptySet() {
            when(repository.findById(compositeId)).thenReturn(Optional.empty());

            Set<FilePermission> actual = service.getPermissions(userKey, fileId);
            assertTrue(actual.isEmpty());
        }

        @Test
        void countByFileId_returnsRepositoryCount() {
            when(repository.countByFileId(fileId)).thenReturn(5L);
            assertEquals(5L, service.countByFileId(fileId));
        }

        @Test
        void deleteByFileId_returnsDeletedCount() {
            when(repository.deleteByFileId(fileId)).thenReturn(2L);
            assertEquals(2L, service.deleteByFileId(fileId));
        }
    }
}
