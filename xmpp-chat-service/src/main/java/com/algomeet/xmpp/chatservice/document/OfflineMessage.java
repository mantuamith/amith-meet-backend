package com.algomeet.xmpp.chatservice.document;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Size;

import org.springframework.data.annotation.Id;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Document(collection = "offline_messages")
@CompoundIndexes({
	/**
	 * Used for findByToAndDeletedAtIsNullOrderByIdAsc
	 */
	@CompoundIndex(name = "active_messages_stream_idx", def = "{'to': 1, 'id': 1}", partialFilter = "{'deletedAt': null}"),

	/**
	 * Used for findByIdAndFromAndDeletedAtIsNull
	 */
	@CompoundIndex(name = "active_lookup_by_sender_idx", def = "{'from': 1, 'id': 1}", partialFilter = "{'deletedAt': null}"),

	/**
	 * Used for deleteByToAndFromAndIdLessThanEqualAndDeletedAtIsNotNull
	 */
	@CompoundIndex(name = "purge_soft_deleted_batch_idx", def = "{'to': 1, 'from': 1, 'deletedAt': 1, 'id': 1}"),

	/**
	 * Used for countByToAndFromAndStanzaIdGreaterThanAndCountableTrue
	 */
	@CompoundIndex(name = "unread_count_idx", def = "{'to': 1, 'from': 1, 'stanzaId': 1}", partialFilter = "{'countable': true}"),

	/**
	 * Used for deleteByIdAndIsAckStanzaTrue
	 */
	@CompoundIndex(
			name = "acknowledged_purge_idx", 
			def = "{'id': 1}", 
			partialFilter = "{'isAck': true}"
			)
})
public class OfflineMessage {
	@Id
	private UUID id;          // The Message ID from the <message id='...'> attribute

	private UUID stanzaId; 

	private UUID from;        // Sender user key / ID

	@Indexed
	private UUID to;          // Receiver user key / ID

	private String messageType; // "chat" or "normal"

	@Size(max = 20000, message = "XML stanza is too large") // Max length 20kb
	private String stanzaXml;   // The raw <message> XML string

	@Builder.Default
	private Instant createdAt = Instant.now(); // Used for XEP-0203 Delayed Delivery stamp

	private Instant deletedAt;

	private Boolean isAckStanza;

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
	 * Automatically deletes messages after 6 months if never delivered.
	 */
	@Indexed(expireAfterSeconds = 6 * 2592000) 
	private Instant expireAt;
}