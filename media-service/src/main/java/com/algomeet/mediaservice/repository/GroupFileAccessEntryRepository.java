package com.algomeet.mediaservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.mediaservice.document.FileAccessEntryDocument;
import com.algomeet.mediaservice.document.GroupFileAccessEntryDocument;

@Repository
public interface GroupFileAccessEntryRepository extends MongoRepository<GroupFileAccessEntryDocument, String> {

    // Leverages idx_group_file prefix to find all files a specific user can access
    List<FileAccessEntryDocument> findByGroupId(UUID groupId);

    // Leverages idx_file_user prefix to find all users who have access to a specific file
    List<FileAccessEntryDocument> findByFileId(UUID fileId);
    
    /**
     * Counts the number of access entries (users) for a specific file.
     * Natively optimized by MongoDB using the prefix of 'idx_file_groupId' ({ fileId: 1, groupId: 1 }).
     */
    long countByFileId(UUID fileId);
    
    /**
     * Deletes all access control entries linked to a specific file.
     * Natively uses the left-most prefix of 'idx_file_group' for an optimized bulk deletion.
     * * @return The number of deleted documents.
     */
    long deleteByFileId(UUID fileId);
}