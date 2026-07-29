package com.algomeet.xmpp.chatservice.document;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Document(collection = "conversation_preferences") 
@CompoundIndexes({
    // Primary index for retrieving user's conversation preferences sorted by seq
    @CompoundIndex(name = "idx_userKey_pinnedSeq", def = "{'_id.userKey': 1, 'pinnedSeq': 1}")
})
public class ConversationPreference {	
	@Id
	private ConversationPreferenceId id;
	
	private UUID peerKey;	
	private UUID groupId;
		
	private Boolean pinned;
	private UUID pinnedSeq;
	private Instant pinnedAt;   

	private Boolean muted;           
	private Instant muteUntil;

	private Boolean archived;
	private Instant archivedAt;

	// MongoDB uses this field to calculate the expiration.
	// Ensure this is set to Instant.now() or new Date() when saving.
	// It is used also for XEP-0203 Delayed Delivery stamp
	@Builder.Default
	private Instant createdAt = Instant.now(); 
	
	private Instant updatedAt;
}
