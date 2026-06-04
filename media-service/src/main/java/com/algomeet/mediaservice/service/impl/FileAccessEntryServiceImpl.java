package com.algomeet.mediaservice.service.impl;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.mediaservice.document.FileAccessEntryDocument;
import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.repository.FileAccessEntryRepository;
import com.algomeet.mediaservice.service.FileAccessEntryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAccessEntryServiceImpl implements FileAccessEntryService {
    private final FileAccessEntryRepository repository;
    private final MongoTemplate mongoTemplate;

    /**
     * Atomically grants permissions to a user for a specific file.
     * Prevents duplicate execution paths using the updateRequestId guard.
     */
    public boolean grantAccess(UUID userKey, UUID fileId, Set<FilePermission> permissions, UUID messageId) {
        String compositeId = generateCompositeId(userKey, fileId);
        
        // Fast-path check: Protect against simple sequential network or client retries
        FileAccessEntryDocument existing = repository.findById(compositeId).orElse(null);

        if (existing != null && 
            existing.getReferencingMessageIds() != null && 
            existing.getReferencingMessageIds().contains(messageId)) {
            log.debug("Already processed messageId: {} for compositeId: {}", messageId, compositeId);
            return false;
        }
        
        // 1. Keep the upsert target strictly bound to the _id. 
        // This ensures MongoDB cleanly initializes the document if it doesn't exist.
        Query query = Query.query(Criteria.where("_id").is(compositeId));

        // 2. Define our modifiers. By checking existing state dynamically, we can determine 
        // if this specific messageId is a newcomer.
        Update update = new Update()
                .setOnInsert("userKey", userKey)
                .setOnInsert("fileId", fileId)
                .addToSet("permissions").each(permissions);

        // If the document is brand new, or it exists but doesn't have this messageId yet,
        // we increment the refCount and append the new message token.
        if (existing == null || existing.getReferencingMessageIds() == null || !existing.getReferencingMessageIds().contains(messageId)) {
            update.addToSet("referencingMessageIds", messageId);
        }

        // 3. Execute the atomic operation
        var result = mongoTemplate.upsert(query, update, FileAccessEntryDocument.class);

        if (result.getModifiedCount() == 0 && result.getUpsertedId() == null) {
        	log.debug("Execution no-op or skipped for ID: {}", compositeId);
        } else {
        	log.info("Successfully granted/updated permissions for composite ID: {}", compositeId);
        }   
        
        return !(result.getModifiedCount() == 0 && result.getUpsertedId() == null);
    }

    /**
     * Atomically revokes/decrements access. When refCount drops to or below 0, 
     * the permission document is cleanly purged from disk.
     */
    public boolean revokeAccess(UUID userKey, UUID fileId, UUID messageId) {
        String compositeId = generateCompositeId(userKey, fileId);

        // 1. Target the exact document, ensuring the messageId is actually present before modifying
        Query query = Query.query(
                Criteria.where("_id").is(compositeId)
                        .and("referencingMessageIds").in(messageId)
        );

        // 2. Atomically decrement the counter and pull the specific UUID from the set
        Update update = new Update()
                .pull("referencingMessageIds", messageId);

        // 3. Execute the atomic update and return the modified document state
        FileAccessEntryDocument updatedDoc = mongoTemplate.findAndModify(
                query, 
                update, 
                FindAndModifyOptions.options().returnNew(true), 
                FileAccessEntryDocument.class
        );

        // 4. Safe delete phase: Guard against concurrent reference modifications
        if (updatedDoc != null && CollectionUtils.isEmpty(updatedDoc.getReferencingMessageIds())) {
            // Target the delete ONLY if refCount is still 0 (guards against another thread adding a share concurrently)
            Query deleteQuery = Query.query(
            		Criteria.where("_id").is(compositeId).and("referencingMessageIds").size(0)
            );
            var result = mongoTemplate.remove(deleteQuery, FileAccessEntryDocument.class);
            
            if (result.getDeletedCount() > 0) {
                log.info("Ref count reached 0. Cleared access entry document for: {}", compositeId);
            } else {
                log.info("Delete bypassed: Document was re-shared or updated concurrently for ID: {}", compositeId);
            } 
        }
        
        return (updatedDoc != null);
    }

    /**
     * Direct check to verify access vectors
     */
    public boolean hasAccess(UUID userKey, UUID fileId) {
        return repository.existsById(generateCompositeId(userKey, fileId));
    }

    /**
     * Fetch all active permissions for a target user-file match
     */
    public Set<FilePermission> getPermissions(UUID userKey, UUID fileId) {
        return repository.findById(generateCompositeId(userKey, fileId))
                .map(FileAccessEntryDocument::getPermissions)
                .orElse(Collections.emptySet());
    }

    public long countByFileId(UUID fileId) {
    	return repository.countByFileId(fileId);
    }
    
    public long deleteByFileId(UUID fileId) {
    	return repository.deleteByFileId(fileId);
    }
    
    private String generateCompositeId(UUID userKey, UUID fileId) {
        return userKey.toString() + "_" + fileId.toString();
    }
}