package com.algomeet.mediaservice.service.impl;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.mediaservice.document.FilePermission;
import com.algomeet.mediaservice.document.GroupFileAccessEntryDocument;
import com.algomeet.mediaservice.repository.GroupFileAccessEntryRepository;
import com.algomeet.mediaservice.service.GroupFileAccessEntryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupFileAccessEntryServiceImpl implements GroupFileAccessEntryService {
    private final GroupFileAccessEntryRepository repository;
    private final MongoTemplate mongoTemplate;

    /**
     * Atomically grants permissions to a user for a specific file.
     * Prevents duplicate execution paths using the updateRequestId guard.
     */
    public boolean grantAccess(UUID groupId, UUID fileId, Set<FilePermission> permissions) {
        String compositeId = generateCompositeId(groupId, fileId);
                
        // 1. Keep the upsert target strictly bound to the _id. 
        // This ensures MongoDB cleanly initializes the document if it doesn't exist.
        Query query = Query.query(Criteria.where("_id").is(compositeId));

        // 2. Define our modifiers. By checking existing state dynamically, we can determine 
        // if this specific messageId is a newcomer.
        Update update = new Update()
                .setOnInsert("groupId", groupId)
                .setOnInsert("fileId", fileId)
                .addToSet("permissions").each(permissions.toArray());

        // 3. Execute the atomic operation
        var result = mongoTemplate.upsert(query, update, GroupFileAccessEntryDocument.class);

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
    public void revokeAccess(UUID groupId, UUID fileId) {
        String compositeId = generateCompositeId(groupId, fileId);        
        repository.deleteById(compositeId);        
        
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
    public Set<FilePermission> getPermissions(UUID groupId, UUID fileId) {
        return repository.findById(generateCompositeId(groupId, fileId))
                .map(GroupFileAccessEntryDocument::getPermissions)
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