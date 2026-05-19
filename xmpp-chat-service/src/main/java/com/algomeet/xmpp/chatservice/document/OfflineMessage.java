package com.algomeet.xmpp.chatservice.document;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Size;

import org.springframework.data.annotation.Id;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Document(collection = "offline_messages")
@CompoundIndex(
		name = "active_offline_messages_idx",
		def = "{'to': 1, 'id': 1}",
		partialFilter = "{'deletedAt': null}" 
		)

@CompoundIndex(
		name = "to_id_idx",
		def = "{'to': 1, 'id': 1}"
		)

@CompoundIndex(
		name = "from_id_partial_idx",
		def = "{'from': 1, 'id': 1}",
		partialFilter = "{'deletedAt': null}" 
		)
public class OfflineMessage {
	@Id
	private UUID id;          // The stanza ID from the <message id='...'> attribute

	private String from;        // Sender user key / ID

	@Indexed
	private String to;          // Receiver user key / ID

	private String messageType; // "chat" or "normal"

	@Size(max = 20000, message = "XML stanza is too large") // Max length 20kb
	private String stanzaXml;   // The raw <message> XML string

	@Builder.Default
	private Instant createdAt = Instant.now(); // Used for XEP-0203 Delayed Delivery stamp

	private Instant deletedAt;

	/**
	 * Optional: MongoDB TTL (Time To Live) index.
	 * Automatically deletes messages after 6 months if never delivered.
	 */
	@Indexed(expireAfterSeconds = 6 * 2592000) 
	private Instant expireAt;
}