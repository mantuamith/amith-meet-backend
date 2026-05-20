package com.algomeet.xmpp.chatservice.document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "muc_messages")
// 1. COMPOUND INDEX for MAM Queries (Room + Sequential ID)
@CompoundIndexes({
	// Optimized for the specific findByRoomIdAndIdGreaterThan... query
	@CompoundIndex(name = "room_id_to_idx", def = "{'roomId': 1, 'id': 1, 'to': 1}"),    
	// Existing cursor index for recentUpdates logic
	@CompoundIndex(name = "room_updatecursorid_seq_idx", def = "{'roomId': 1, 'updateCursorId': 1}"),

	// Optimized for Sync (Forward) and History (Backward)
	// roomId: 1 (Equality) 
	// id: -1 (Optimized for "Latest messages first")
	// to: 1 (Filter)
	@CompoundIndex(name = "idx_muc_history_optimized", def = "{'roomId': 1, 'id': -1, 'to': 1}"),
	
	/**
	 * Optimized index for incremental synchronization of MUC history.
	 * 
	 * <p>This index follows the <b>ESR (Equality, Sort, Range)</b> rule to handle 
	 * billion-scale message datasets with millisecond latency:</p>
	 * <ul>
	 *   <li><b>Equality (roomId):</b> Quickly narrows the search space to a specific chat room.</li>
	 *   <li><b>Sort/Range (updateCursorId):</b> Provides a high-performance anchor for 
	 *       incremental sync (e.g., "Give me everything since my last cursor").</li>
	 *   <li><b>Range (id):</b> Allows the query to be "covered" by the index when 
	 *       applying an upper-bound limit (limitId), preventing the database from 
	 *       needing to fetch documents from disk to verify the ID constraint.</li>
	 * </ul>
	 * 
	 * <p>Crucial for maintaining high throughput in {@code findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualOrderByIdAsc}.</p>
	 */
	@CompoundIndex(
			name = "idx_muc_sync_optimized", 
			def = "{'roomId': 1, 'updateCursorId': 1, 'id': 1}"
			),
	
	@CompoundIndex(
		    name = "idx_muc_unread_count_optimized", 
		    def = "{ 'roomId': 1, 'countable': 1, 'messageId': 1, 'to': 1 }",
		    partialFilter = "{ 'deletedAt': null, 'countable': true }"
		)
})
public class MucMessage {    
	@Id
	private UUID id;           // UUIDv7 or Sequential String

	// 2. UNIQUE INDEX for Message ID
	// Prevents duplicate messages if a client retries a send
	@Indexed(unique = true, sparse = true)
	private UUID messageId; 

	// Indexed via the Compound Index above, but good for simple lookups
	private UUID roomId;

	private UUID from;

	// Used for DIRECT PRIVATE MESSAGE (PM) WITHIN MUC 
	@Indexed
	private UUID to;

	@Size(max = 20000, message = "XML stanza is too large") // Max length 20kb
	private String stanzaXml;

	private Instant deletedAt;

	private Set<String> hiddenFromUserKeys = new HashSet<>();
		
	// Indicates whether this record represents the current starting point of the room conversation.
	// Used to synchronize hard-deleted messages across local devices.
	@Transient
    private Boolean startOfRoomConversation = false;

	/**
	 * Monotonically increasing UUIDv7 used as a synchronization cursor for this message record.
	 *
	 * Updated whenever the message state changes (e.g. hide, delete, edit, reaction).
	 *
	 * Enables efficient incremental sync queries such as:
	 * find records where updateCursorId > client's last known cursor.
	 *
	 * UUIDv7 is used so values remain lexicographically sortable by creation time.
	 *
	 * This is a server-side update marker and is different from:
	 * - id        : MongoDB document identifier
	 * - messageId : Original client/XMPP message identifier
	 */
	@Indexed(unique = true, sparse = true)
	private UUID updateCursorId;

	private Instant createdAt = Instant.now();
	
	/**
	 * Indicates whether this message should increment the unread message count.
	 *
	 * Countable messages typically include:
	 * - normal chat messages
	 * - OMEMO encrypted messages
	 * - attachments
	 *
	 * Non-countable messages typically include:
	 * - message edits/corrections
	 * - delivery receipts
	 * - chat markers
	 * - reactions
	 * - typing indicators
	 * - retraction events
	 */
	private Boolean countable;
	
    /**
     * Optional: MongoDB TTL (Time To Live) index.
     * Automatically deletes messages after 12 months if never delivered.
     */
    @Indexed(expireAfterSeconds = 12 * 2592000) 
    private Instant expireAt;
}