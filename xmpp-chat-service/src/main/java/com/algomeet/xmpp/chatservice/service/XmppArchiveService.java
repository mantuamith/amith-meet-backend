package com.algomeet.xmpp.chatservice.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for managing XMPP Message Archive Management (MAM) as per XEP-0313.
 * <p>
 * This service handles the persistent storage of MUC stanzas and provides 
 * reactive querying capabilities to allow clients to synchronize chat history.
 * </p>
 * * @author Algomeet Core Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XmppArchiveService {    
	private final MucMessageRepository repository;
	private final LocalStanzaDispatcher localStanzaDispatcher;

	/**
	 * Persists a room event (message or signaling) to the archive.
	 *
	 * @param xml        The raw XML stanza content.
	 * @param info       Metadata extracted from the stanza (ID, Category, Encryption status).
	 * @param toRoomId   The internal ID of the room destination.
	 * @param toMucMember The specific recipient (for private MUC messages) User Key.
	 * @param from       The sender's User Key.
	 * @param internalId The unique internal ID (ULID/Snowflake) for database indexing.
	 * @return A {@link Mono} containing the saved {@link MucMessage}.
	 */
	public Mono<MucMessage> archiveEvent(String xml, StanzaInfo info, String toRoomId, String toMucMember, String from, String internalId) {
		MucMessage event = MucMessage.builder()
				.id(internalId)
				.messageId(info.getMessageId()) 
				.roomId(toRoomId)
				.from(from)
				.to(toMucMember)
				.stanzaXml(xml)
				.category(info.getCategory())
				.refersTo(info.getTargetId()) 
				.isE2EE(info.isE2EE())
				.build();

		return repository.save(event);
	}

	/**
	 * Routes MAM queries to the appropriate pagination handler based on RSM fields.
	 * * @param ctx       Netty context.
	 * @param roomId    Target MUC room.
	 * @param xml       The MAM <query/> stanza.
	 * @param principal User requesting the history.
	 */
	public void fetchMucArchive(ChannelHandlerContext ctx, String roomId, String xml, XmppPrincipal principal) {
		String afterId = XmppStanzaUtil.getFieldValue(xml, "after-id");
		String beforeId = XmppStanzaUtil.getFieldValue(xml, "before-id");
		int maxResults = XmppStanzaUtil.getRsmMax(xml, 50);
		String queryId = XmppStanzaUtil.getAttribute(xml, "id");

		// Strategy: If 'after' is present, we move forward in time.
		// Otherwise (or if 'before' is present), we move backward into history.
		if (StringUtils.hasText(afterId)) {
			loadAfterId(ctx, roomId, afterId, maxResults, queryId, principal);
		} else {
			loadBeforeId(ctx, roomId, beforeId, maxResults, queryId, principal);
		}
	}

	/**
	 * Handles "Load Newer" or "Sync from last disconnect" logic.
	 * Uses ASC order to stream messages from the oldest in the set to the newest.
	 */
	private void loadAfterId(ChannelHandlerContext ctx, String roomId, String afterId, int maxResults, String queryId, XmppPrincipal principal) {
		log.debug("MAM Request for Room {}: afterId={}, max={}", roomId, afterId, maxResults);

		PageRequest pageRequest = PageRequest.of(0, maxResults);

		repository.findByRoomIdAndIdGreaterThanOrderByIdAsc(roomId, afterId, pageRequest)
		.filter(msg -> isAuthorized(msg, principal))
		.concatMap(msg -> dispatchMamResult(msg, queryId, principal))
		.doOnComplete(() -> sendFin(queryId, principal))
		.subscribe();
	}

	/**
	 * Handles "Infinite Scroll" logic (loading older messages).
	 * Uses DESC order to find the N messages immediately preceding the 'beforeId'.
	 */
	private void loadBeforeId(ChannelHandlerContext ctx, String roomId, String beforeId, int maxResults, String queryId, XmppPrincipal principal) {
		log.info("MAM Request for Room {}: beforeId={}, max={}", roomId, beforeId, maxResults);

		PageRequest pageRequest = PageRequest.of(0, maxResults);

		// Determine the source Flux based on whether beforeId is present
		Flux<MucMessage> messageFlux = (beforeId == null || beforeId.trim().isEmpty()) 
				? repository.findByRoomIdOrderByIdDesc(roomId, pageRequest)
						: repository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId, pageRequest);

		messageFlux
		.filter(msg -> isAuthorized(msg, principal))
		.concatMap(msg -> dispatchMamResult(msg, queryId, principal))
		.doOnComplete(() -> sendFin(queryId, principal))
		.doOnError(e -> log.error("MAM failure for room {}", roomId, e))
		.subscribe();
	}

	/**
	 * Filters messages to ensure Private Messages within a MUC are only visible to the recipient.
	 */
	private boolean isAuthorized(MucMessage msg, XmppPrincipal principal) {
		return msg.getTo() == null || msg.getTo().equalsIgnoreCase(principal.getUserKey());
	}

	/**
	 * Wraps a database record into a XEP-0313 <result/> stanza and dispatches it.
	 */
	private Mono<Void> dispatchMamResult(MucMessage msg, String queryId, XmppPrincipal principal) {
		// Determine the timestamp (assuming your msg object has a getTimestamp or similar)
		// Format must be ISO-8601: 2026-04-27T10:16:25Z
		String timestamp = XmppStanzaUtil.formatTimestamp(msg.getCreatedAt()); 

		String mamResult = String.format(
				"<message to='%s'>" +
						"<result xmlns='urn:xmpp:mam:2' %s id='%s'>" +
						"<forwarded xmlns='urn:xmpp:forward:0'>" +
						"<delay xmlns='urn:xmpp:delay' stamp='%s'/>" + // <--- Added delay tag
						"%s" + // The original archived XML
						"</forwarded>" +
						"</result>" +
						"</message>",
						principal.getBareJid(),
						(queryId != null ? "queryid='" + queryId + "'" : ""),
						msg.getId(),
						timestamp,
						msg.getStanzaXml()
				);        
		return Mono.<Void>create(sink -> {
			localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), mamResult);
			sink.success();
		});
	}

	/**
	 * Signals the end of the MAM stream.
	 */
	private void sendFin(String queryId, XmppPrincipal principal) {
		String fin = String.format(
				"<iq type='result' to='%s' %s>" +
						"<fin xmlns='urn:xmpp:mam:2' complete='true'>" +
						"<set xmlns='http://jabber.org/protocol/rsm'/>" +
						"</fin>" +
						"</iq>",
						principal.getBareJid(),
						(queryId != null ? "id='" + queryId + "'" : "")
				);
		localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), fin);
	}
}