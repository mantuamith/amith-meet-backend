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
@Document(collection = "group_file_access_entries")
@CompoundIndexes({
    @CompoundIndex(name = "idx_group_file", def = "{ 'groupId': 1, 'fileId': 1 }"),
    @CompoundIndex(name = "idx_file_group", def = "{ 'fileId': 1, 'groupId': 1 }")
})
public class GroupFileAccessEntryDocument {
    @Id
    private String id; // <groupId>_<fileId>
    private UUID groupId;
    private UUID fileId;
    
    /**
     * READ, SHARE, DELETE
     */
    @Field("permissions")
    private Set<FilePermission> permissions;
}
