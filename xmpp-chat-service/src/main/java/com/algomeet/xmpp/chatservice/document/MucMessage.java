package com.algomeet.xmpp.chatservice.document;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
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
	 * and findFirstByRoomIdAndIdLessThanAndToIsNullOrEqualtoUserkeyAndNotHiddenOrderByIdDesc
	 */
	// Index A: For public/room-wide messages
	@CompoundIndex(name = "idxMuc_roomId_idDesc_createdA_publicPartial", 
			def = "{'roomId': 1, 'id': -1, 'createdAt': 1}",
		    partialFilter = "{ 'deletedAt': null }"),
	// Index B: For direct private messages inside the room
	// Used for MucMessageService.getConversations() 
	@CompoundIndex(name = "idxMuc_room_to_idDesc_createdAt_privatePartial", 
			def = "{'roomId': 1, 'to': 1, 'id': -1, 'createdAt': 1}",
			partialFilter = "{ 'deletedAt': null }"),

	/**
	 * Incremental message update synchronization.
	 *
	 * Used to synchronize updates to the local device's message copy.
	 * findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualAndCreatedAtGreaterThanOrderByIdDesc()
	 */
	@CompoundIndex(
		    name = "idxMuc_roomId_updateCursorId_idDesc_createdAt", 
		    def = "{ 'roomId': 1, 'updateCursorId': 1, 'id': -1, 'createdAt': 1 }"
		),

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
			def = "{ 'roomId': 1, 'id': 1, 'createdAt': 1 }"),
	
	/**
	 * Find reactions and edits
	 * Used for 
	 * HidetUtil.hideRelatedMessages() and RetractUtil.retractRelatedMessages()
	 */
	@CompoundIndex(
		    name = "idxMuc_roomId_targetMessageId_partial", 
		    def = "{ 'roomId': 1, 'targetMessageId': 1 }",
		    partialFilter = "{ 'targetMessageId': { '$exists': true } }" 
		),
	/**
	 * Used for findByPurgeAtLessThanEqual
	 */
	@CompoundIndex(
		    name = "idxMuc_purgeAt_id", 
		    def = "{ 'purgeAt': 1, '_id': 1 }"
		)
})
public class MucMessage {   
	public static final String FIELD_ID = "id"; // StanzaId
	public static final String FIELD_ROOM_ID = "roomId";
	public static final String FIELD_DELETED_AT = "deletedAt";
	public static final String FIELD_PURGE_AT = "purgeAt";
	public static final String FIELD_CREATED_AT = "createdAt";
	public static final String FIELD_MESSAGE_ID = "messageId";

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
	private UUID to;

	@Size(max = 20000, message = "XML stanza is too large") // Max length 20kb
	private String stanzaXml;

	private Long deletedAt;

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
	
    // Useful for finding all reactions, edits and etc to a specific message
    private UUID targetMessageId;    
    
    // Use to store message attachment file IDs
    private List<UUID> mediaIds;
    
	/** 
	 * Used for configuring deletion date of the chat message.
	 */
	private Instant purgeAt;
}