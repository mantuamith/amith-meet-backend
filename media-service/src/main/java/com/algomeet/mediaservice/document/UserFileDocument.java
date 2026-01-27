package com.algomeet.mediaservice.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "user-files")
@CompoundIndexes({
    @CompoundIndex(
        name = "idx_acl_userKey",
        def = "{ 'access_control_list.userKey': 1 }"
    )
})
public class UserFileDocument {

    @Id
    private String id;

    @Field("filename")
    private String filename;

    /**
     * Storage reference (S3 key / CloudFront path)
     * NOT a public URL
     */
    @Field("absolutePath")
    private String absolutePath;

    @Field("contentType")
    private String contentType;

    @Field("size")
    private Long size;

    @Field("dateCreated")
    private Instant dateCreated;

    @Field("dateLastModified")
    private Instant dateLastModified;

    @Field("dateLastDownloaded")
    private Instant dateLastDownloaded;

    /**
     * Owner userId
     */
    @Indexed
    @Field("owner")
    private String owner;

    /**
     * Access Control List
     */
    @Field("access_control_list")
    private List<FileAccessEntry> accessControlList;
    
    @Field("storage")
    private String storage;
    
    @Indexed
    @Field("cleanupEligibleAt")
    private Instant cleanupEligibleAt;
    
    @Field("isEncrypted")
    private boolean isEncrypted;
}
