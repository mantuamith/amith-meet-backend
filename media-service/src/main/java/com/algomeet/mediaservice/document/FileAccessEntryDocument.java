package com.algomeet.mediaservice.document;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;

@Data
@Document(collection = "file_access_entries")
@CompoundIndexes({
    @CompoundIndex(name = "idx_user_file", def = "{ 'userKey': 1, 'fileId': 1 }"),
    @CompoundIndex(name = "idx_file_user", def = "{ 'fileId': 1, 'userKey': 1 }")
})
public class FileAccessEntryDocument {
    @Id
    private String id; // <userKey>_<fileId>
    private UUID userKey;
    private UUID fileId;
    
    /**
     * READ, SHARE, DELETE
     */
    @Field("permissions")
    private Set<FilePermission> permissions;
    
    /**
     * Identifiers of chat messages that reference this file.
     * Used to track file usage and determine when the file is no longer referenced.
     */
    @Field("referencingMessageIds")
    private Set<UUID> referencingMessageIds;
}
