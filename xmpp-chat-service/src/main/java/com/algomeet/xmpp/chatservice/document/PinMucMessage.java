package com.algomeet.xmpp.chatservice.document;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Document(collection = "pin_muc_messages") 
@CompoundIndexes({
    // 1. Optimizes the main visibility search query with sequence sorting
    @CompoundIndex(name = "idxPin_idGroupId_idPinnedBy_pinnedForEveryone_seqAsc", 
                   def = "{'_id.groupId': 1, '_id.pinnedBy': 1, 'pinnedForEveryone': 1, 'seq': 1}"),
                   
    // 2. Optimizes targeting specific message definitions for lookups or deletions
    @CompoundIndex(name = "idxPin_idGroupId_idMessageId", 
                   def = "{'_id.groupId': 1, '_id.messageId': 1}")
})
public class PinMucMessage {
	@Id
	private PinMucMessageId id;  
	
	private UUID groupId;

	private UUID messageId;
	
	private UUID pinnedBy;
		
	@NotNull
	@Field("seq")
	private UUID seq;
	
	private boolean pinnedForEveryone;
	
	/**
	 * TTL Index for automatic self-deletion.
	 * Setting expireAfterSeconds = 0 tells MongoDB to remove this document 
	 * precisely when the current server time passes this 'expiration' timestamp.
	 * * Defaults to null so messages persist indefinitely unless explicitly overridden.
	 */
	@Builder.Default
	@Indexed(expireAfterSeconds = 0)
	private Instant expiration = null;  
	
	// MongoDB uses this field to calculate the expiration.
	// Ensure this is set to Instant.now() or new Date() when saving.
	// It is used also for XEP-0203 Delayed Delivery stamp
	@Builder.Default
	private Instant createdAt = Instant.now(); 
}