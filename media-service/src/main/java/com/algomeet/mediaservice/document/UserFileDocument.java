package com.algomeet.mediaservice.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "user-files")
@CompoundIndexes({
	// 1. For looking up an owner's files sorted by creation date (Dashboard/File Manager view)
	@CompoundIndex(
			name = "idx_owner_dateCreated", 
			def = "{ 'owner': 1, 'dateCreated': -1 }"
			),

	// 2. Optimized partial index for background retention/cleanup workers
	@CompoundIndex(
			name = "idx_cleanup_partial", 
			def = "{ 'cleanupEligibleAt': 1, 'storage': 1 }",
			partialFilter = "{ 'cleanupEligibleAt': { $exists: true } }"
			),

	// 3. For identifying storage system distributions and tracking file space metrics
	@CompoundIndex(
			name = "idx_owner_storage_size", 
			def = "{ 'owner': 1, 'storage': 1, 'size': 1 }"
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

    @Field("dateLastRead")
    private Instant dateLastRead;

    /**
     * Owner userId
     */
    @Indexed
    @Field("owner")
    private String owner;
    
    @Field("storage")
    private String storage;
    
    @Indexed
    @Field("cleanupEligibleAt")
    private Instant cleanupEligibleAt;
    
    @Field("isEncrypted")
    private boolean isEncrypted;

    /** Chat session this file belongs to (null when not sent in a chat). */
    @Indexed
    @Field("conversationId")
    private String conversationId;

    /** Tracks whether the file was uploaded as a chat attachment or generic media. */
    @Field("uploadContext")
    private String uploadContext;

    /** Image width in pixels (null for non-image files). */
    @Field("mediaWidth")
    private Integer mediaWidth;

    /** Image height in pixels (null for non-image files). */
    @Field("mediaHeight")
    private Integer mediaHeight;

    /** Media duration in seconds (null when not extracted). */
    @Field("durationSeconds")
    private Double durationSeconds;
}
