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
	 * Used for findByToAndDeliveredAtIsNullOrderByIdAsc
	 */
	@CompoundIndex(name = "idxOffline_to_idAsc", def = "{'to': 1, 'id': 1}", partialFilter = "{'deliveredAt': null}"),

	/**
	 * Used for findByIdAndFromAndDeliveredAtIsNull
	 */
	@CompoundIndex(name = "idxOffline_from_id", def = "{'from': 1, 'id': 1}", partialFilter = "{'deliveredAt': null}"),

	/**
	 * Used for deleteByToAndFromAndStanzaIdLessThanEqualAndDeliveredAtIsNotNull
	 */
	@CompoundIndex(name = "idxOffline_to_from_deliveredAt_stanzaId", def = "{'to': 1, 'from': 1, 'deliveredAt': 1, 'stanzaId': 1}"),

	/**
	 * Used for countByToAndFromAndStanzaIdGreaterThanAndCountableTrue
	 */
	@CompoundIndex(name = "idxOffline_to_from_stanzaId", def = "{'to': 1, 'from': 1, 'stanzaId': 1}", partialFilter = "{'countable': true}"),

	/**
	 * Used for deleteByIdAndIsAckStanzaTrue
	 */
	@CompoundIndex(
			name = "idxOffline_id", 
			def = "{'id': 1}", 
			partialFilter = "{'isAck': true}"
			),
	
	/**
	 * Used for findByFromOrderByStanzaIdDesc
	 * Satisfies Equality (from) and Sort (stanzaId DESC) cleanly.
	 */
	@CompoundIndex(
	    name = "idxOffline_from_stanzaIdDesc", 
	    def = "{'from': 1, 'stanzaId': -1}"
	),
	/**
	 * Used for deleteByToAndFromAndDeliveredAtIsNotNullAndStanzaIdLessThanEqual
	 * Follows ESR: Equality (to, from, deliveredAt) -> Range (id)
	 */
	@CompoundIndex(name = "idxOffline_to_from_deliveredAt_stanzaId", def = "{'to': 1, 'from': 1, 'deliveredAt': 1, 'stanzaId': 1}")

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

	/**
	 * Timestamp indicating when the message was marked as delivered to the client.
	 * Presence of this value indicates the message is ready for permanent deletion/cleanup.
	 */
	private Instant deliveredAt;

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