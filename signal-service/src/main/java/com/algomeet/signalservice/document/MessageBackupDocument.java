package com.algomeet.signalservice.document;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "message_backups")
@CompoundIndexes({
    /**
     * 1. Message history + range queries per conversation (ESR pattern)
     * Covers: findByConversationIdAndStanzaIdLessThan, findByConversationIdAndStanzaIdGreaterThan,
     *         deleteByUserKeyAndConversationId, getConversationStorageStats,
     *         and findFirstByUserKeyAndConversationIdOrderByStanzaIdAsc.
     */
    @CompoundIndex(
        name = "idxMsg_userKey_updateCursorId_stanzaIdAsc", 
        def = "{'userKey': 1, 'conversationId': 1, 'stanzaId': -1}"
    ),
    
    /**
     * 2. Incremental sync cursor (update tracking + ordering)
     * Covers high-frequency background synchronization loops matching user modifications.
     * MessageBackupService.getMessageUpdates
     */
    @CompoundIndex(
        name = "idxMsg_userKey_messageId", 
        def = "{'userKey': 1, 'updateCursorId': 1, 'stanzaId': 1}"
    ),
    
    /**
     * 3. Direct message lookup + lightweight ordering
     * Covers: findByMessageIdAndUserKey, and any chronological receipt processing (Read/Delivered states).
     */
    @CompoundIndex(
        name = "idxMsg_userKey", 
        def = "{'userKey': 1, '_id': 1}"
    ),
    
    /**
     * 4. Account deletion / cleanup by owner
     * Covers: deleteByUserKey(UUID userKey) during account offboarding or device un-pairing actions.
     */
    @CompoundIndex(
        name = "idxMsg_userKey", 
        def = "{'userKey': 1}"
    ),
    
    /**
     * 5. Inbox / conversation listing (latest-first scan). Used for finding user conversations 
     * MessageBackupService.findUniqueConversationsWithFullDetails
     */
    @CompoundIndex(
    	    name = "idxMsg_userKey_stanzaIdDesc_conversationId", 
    	    def = "{'userKey': 1, 'stanzaId': -1, 'conversationId': 1}"
    	),
    
    /**
     * 6. Read-state bulk update per conversation
     * MessageBackupService.updateStatus
     */
    @CompoundIndex(
        name = "idxMsg_conversationId_stanzaId_readAt", 
        def = "{'conversationId': 1, 'stanzaId': 1, 'readAt': 1}"
    )
})
public class MessageBackupDocument {
	// These constants match the @Field names or the variable names
    public static final String FIELD_CONVERSATION_ID = "conversationId";
    public static final String FIELD_USER_KEY = "userKey";
    public static final String FIELD_STANZA_ID = "stanzaId";
    public static final String FIELD_UPDATE_CURSOR_ID = "updateCursorId";
    public static final String FIELD_MESSAGE_ID = "messageId";
    public static final String FIELD_SENDER_KEY = "senderKey";
    public static final String FIELD_RECEIVER_KEY = "receiverKey";
    public static final String FIELD_ENCRYPTED_MSG = "encryptedMessage";
    public static final String FIELD_ALGORITHM = "algorithm";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_SALT = "salt";
    public static final String FIELD_SENT_AT = "sentAt";
    public static final String FIELD_DELIVERED_AT = "deliveredAt";
    public static final String FIELD_READ_AT = "readAt";
    public static final String FIELD_DELETED_AT = "deletedAt";
    public static final String FIELD_RETRACTED_AT = "retractedAt";
    public static final String FIELD_EDIT_COUNT = "editCount";
    public static final String FIELD_TIMESTAMP = "timestamp";
    
	@Id
	private UUID messageId;

	/**
	 * Globally unique and lexicographically sortable server-generated message identifier.
	 * chronological sorting, pagination cursors, and cross-device synchronization.
	 */
	@Indexed
	private UUID stanzaId;

	/** 
	 * Deterministic conversation identifier for this message record.
	 * Composed of the record owner's userKey and the other chat participant/entity key.
	 * Used to group and query messages belonging to the same conversation.
	 * 
	 * Format: <User Key>_<Peer User Key>
	 * 
	 * Auto populated field.
	 */
    @Schema(hidden = true)
	private String conversationId;

    /**
     * Auto populated field.
     */
    @Schema(hidden = true)
	@Field("userKey")
	private UUID userKey;   

    @NotNull
	@Field("senderKey")
	private UUID senderKey; 

	@NotNull
	@Field("receiverKey")
	private UUID receiverKey;     

	@NotEmpty
	@Size(max = 20000)
	@Field("encryptedMessage")
	@Pattern(
			regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
			message = "{invalid-base64-format}"
			)
	private String encryptedMessage; 

	@Field("sentAt")
	private Long sentAt;

	@Field("deliveredAt")
	private Long deliveredAt;

	@Field("readAt")
	private Long readAt;
	
	// Soft delete / tombstone (for retention/compaction)
	private Long deletedAt;
	
	private Long retractedAt;
	
    // Useful for finding all reactions, replies and etc to a specific message
    @Indexed
    private String refersTo;      
  
    private Integer editCount;
    
    @Transient
    private Boolean startOfConversation = false; // Initialize to avoid null-omission
    
    @Indexed(unique = true, sparse = true)
    @io.swagger.v3.oas.annotations.media.Schema(
        description = "Cursor used for incremental sync ordering. " +
                      "Set this to the stanza-id of the edit (replace) request when a message is updated; " +
                      "otherwise leave it blank." +
                      "This field is UUID v7 and used for cursor-based lookup.",
        example = "019e537d-31a0-7556-a160-7ac448312343"
    )
    private UUID updateCursorId;

	@Field("size")
	private Long size;
	
	/** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
	@Size(max = 32)
	@Field("algorithm")
	private String algorithm;

	/** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
	@Size(max = 10)
	@Field("version")
	private String version;

	/** Base64-encoded salt value for key derivation (optional but recommended). */
	@Pattern(
			regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
			message = "Invalid base64 format"
			)
	@Size(max = 88)
	@Field("salt")
	private String salt;

	private Instant timestamp = Instant.now();
}
