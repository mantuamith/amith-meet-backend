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
@CompoundIndexes({
	/**
	 * Latest messages, history pagination, conversation feed.
	 * 
	 * Used for retrieving messages findByRoomIdAndIdGreaterThan...Asc(), findByRoomIdAndIdLessThan...Desc() 
	 * queries
	 */
	// Index A: For public/room-wide messages
	@CompoundIndex(name = "idxMuc_roomId_idDesc_createdA_public", def = "{'roomId': 1, 'id': -1, 'createdAt': 1}"),
	// Index B: For direct private messages inside the room
	@CompoundIndex(name = "idxMuc_room_to_idDesc_createdAt_private", def = "{'roomId': 1, 'to': 1, 'id': -1, 'createdAt': 1}"),
	
	/**
	 * Used for MucMessageService.getConversations() 
	 * Important Notice: Be careful on changing the name of this index it's hard coded inside MucMessageService.getConversations().
	 */
	@CompoundIndex(name = "idxMuc_room_to_idDesc_createdAt_private", def = "{'roomId': 1, 'to': 1, 'id': -1, 'createdAt': 1}"),

	/**
	 * Incremental message update synchronization.
	 *
	 * Used to synchronize updates to the local device's message copy.
	 * findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualOrderByIdAsc()
	 */
	@CompoundIndex(name = "idxMuc_roomId_idDesc_updateCursorId", def = "{ 'roomId': 1, 'id': -1, 'updateCursorId': 1 }"),

	/**
	 * Unread message counting.
	 * 
	 * Used for unread counts countUnreadMessages()
	 */
	@CompoundIndex(
		    name = "idxMuc_room_to_id_createdAt", 
		    def = "{ 'roomId': 1, 'to': 1, 'id': 1, 'createdAt': 1 }",
		    partialFilter = "{ 'deletedAt': null, 'countable': true }"
		),
	
	/**
	 * Read state catch-up updates.
	 * 
	 * Used for Read status batch update MucMessageService.bulkMarkRoomMessagesAsRead()
	 */
	@CompoundIndex(
		    name = "idxMuc_room_readAt_id", 
		    def = "{ 'roomId': 1, 'readAt': 1, '_id': 1 }"
		),
	
	/**
	 * Find the first message visible to the user in the room.
	 * 
	 * Used for retrieving the earliest room message created after
	 * the user's join time via:
	 * findFirstByRoomIdAndCreatedAtGreaterThanOrderByCreatedAtAsc()
	 */
	@CompoundIndex(
			name = "idxMuc_roomId_idAsc_createdAt", 
			def = "{ 'roomId': 1, 'id': 1, 'createdAt': 1 }")
})
public class MucMessage {   
	public static final String FIELD_ID = "id"; // StanzaId
	public static final String FIELD_ROOM_ID = "roomId";

	@Id
	private UUID id;           // Stanza ID - UUID v7

	// UNIQUE INDEX for Message ID
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

	private Long deletedAt;
	
	private Long readAt;

	private Set<UUID> hiddenFromUserKeys = new HashSet<>();

	// Indicates whether this record represents the current starting point of the room conversation.
	// Used to synchronize hard-deleted messages across local devices.
	@Transient
	private Boolean startOfRoomConversation = false;

	/**
	 * Monotonically increasing UUIDv7 used as a synchronization cursor for this message record.
	 *
	 * Updated whenever the message state changes (e.g. hide, delete).
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