package com.algomeet.xmpp.chatservice.document;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "muc_room_read_cursors")

@CompoundIndexes({
	/**
	 * Used for findByUserKey
	 */
	@CompoundIndex(name = "idx_cursors_user", def = "{'userKey': 1}"),

	/**
	 * Used for findByUserKeyAndRoomId
	 */
	@CompoundIndex(name = "idx_cursors_user_room", def = "{'userKey': 1, 'roomId': 1}"),

	/**
	 * Used for findByRoomIdAndLastReadSidGreaterThanEqual()
	 * and findByRoomIdIn(Set<UUID> roomIds)
	 */
	@CompoundIndex( name = "idx_room_lastReadSid", def = "{'roomId': 1, 'lastReadSid': 1}")
})
public class MucRoomReadCursor {
	@Id
	private String id; // Structural compound key: String.format("%s_%s", userKey, roomId)

	private UUID userKey;

	private UUID roomId;

	private UUID lastReadMid; // The 'messageId' of the last message read by the user

	private UUID lastReadSid; // The stanzaId 'id' he last message read by the user

	private long lastReadAt;
}