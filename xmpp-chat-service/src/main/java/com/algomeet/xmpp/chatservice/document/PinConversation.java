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
@Document(collection = "pin_conversations") 
@CompoundIndexes({
    // Primary index for retrieving user's pinned conversations sorted by seq
    @CompoundIndex(name = "idx_pinnedBy_seq", def = "{'_id.pinnedBy': 1, 'seq': 1}")
})
public class PinConversation {	
	@Id
	private PinConversationId id;   
	
	private UUID peerKey;
	
	private UUID groupId;

	@NotNull
	@Field("seq")
	private UUID seq;

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
