package com.algomeet.signalservice.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Document(collection = "message_backups")
@CompoundIndexes({
	// 1. For Chat History: Fetching messages within a specific 1:1 chat
    @CompoundIndex(
        name = "idx_conversation_stanza", 
        def = "{'conversationId':1, 'stanzaId':-1}"
    ),
    
    // 2. For the Conversation List: Optimized for ESR (Equality, Sort, Range)
    // By putting timestamp before conversationId, MongoDB can find the 
    // latest unique conversations for a user with minimal index walking.
    @CompoundIndex(
        name = "idx_user_inbox_view", 
        def = "{'userKey': 1, 'timestamp': -1, 'conversationId': 1}"
    )
})
public class MessageBackupDocument {
	@Id
	@Size(max = 56)
	private String messageId;

	/**
	 * Globally unique and lexicographically sortable server-generated message identifier.
	 * Typically derived from ULID and used for stable message references, efficient
	 * chronological sorting, pagination cursors, and cross-device synchronization.
	 */
	@Indexed
	@Size(max = 45)
	private String stanzaId;

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
	@Size(max = 45)
	@Field("userKey")
	private String userKey;   

	@NotEmpty
	@Size(max = 45)
	@Field("senderKey")
	private String senderKey; 

	@NotEmpty
	@Size(max = 45)
	@Field("receiverKey")
	private String receiverKey;     

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
	
    // Useful for finding all reactions, replies and etc to a specific message
    @Indexed
    private String refersTo;      
  
    private Integer editCount;
    
    @Schema(hidden = true)
    @Indexed(unique = true, sparse = true)
    private String updateCursorId;

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
