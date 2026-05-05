package com.algomeet.xmpp.chatservice.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.stanza.MessageRetractStanza;
import com.algomeet.xmpp.chatservice.stanza.ViewManagementSyncStanza;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.data.domain.Pageable;

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
	private final GroupCacheService groupCacheService;
	private final XmppUtil xmppUtil;
	private final DomainProperties domainProperties;
	private final ReactiveMongoTemplate reactiveMongoTemplate;
	private final JidUtil jidUtil;

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

		// Get room details
		MucRoomDto room = groupCacheService.getCachedGroup(roomId);
	
		if (room == null || !(room.getMembers().stream()
				.anyMatch(m -> principal.getUserKey().equalsIgnoreCase(m.getUserKey())))) {
			log.error("Unauthorized access to room {}", roomId);
			xmppUtil.sendError(ctx, queryId, principal.getBareJid(), domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
					XmppErrorConditions.INTERNAL_SERVER_ERROR, "Unauthorized access");
			return;
		}
		
		// Strategy: If 'after' is present, we move forward in time.
		// Otherwise (or if 'before' is present), we move backward into history.
		if (StringUtils.hasText(afterId)) {
			syncWithRetry(roomId, afterId, principal, queryId, maxResults);
			
			// Sync recent updates
			syncRecentRoomUpdates(roomId, afterId, principal);
		} else {
			loadBeforeId(ctx, roomId, beforeId, maxResults, queryId, principal);
		}
	}

	/**
	 * Handles "Load Newer" or "Sync from last disconnect" logic.
	 * Uses ASC order to stream messages from the oldest in the set to the newest.
	 */
	private void syncWithRetry(String roomId, String currentAfterId, XmppPrincipal principal, String queryId, int maxResults) {
	    Pageable pageRequest = PageRequest.of(0, maxResults);
	    
	    repository.findByRoomIdAndIdGreaterThanAndToIsNullOrEqualtoUserkeyOrderByIdAsc(
	            roomId, currentAfterId, principal.getUserKey(), pageRequest)
	        .collectList()
	        // Explicitly define the generic type <Void> for flatMap
	        .<Void>flatMap(list -> {

	            List<MucMessage> authorizedMessages = list.stream()
	                .filter(msg -> isAuthorized(msg, principal))
	                .collect(Collectors.toList());

	            // 1. RE-QUERY LOGIC: Empty authorized list but DB had data
	            if (authorizedMessages.isEmpty() && !list.isEmpty()) {
	                String newAfterId = list.get(list.size() - 1).getId();
	                log.debug("No authorized messages in batch. Re-querying from {}", newAfterId);
	                
	                // Use fromRunnable to return Mono<Void> and trigger the next hop
	                return Mono.fromRunnable(() -> 
	                    syncWithRetry(roomId, newAfterId, principal, queryId, maxResults)
	                );
	            }

	            // 2. DISPATCH LOGIC: Process authorized messages or handle end of stream
	            return Flux.fromIterable(authorizedMessages)
	                .concatMap(msg -> dispatchMamResult(msg, queryId, principal))
	                // .then() ensures the result is Mono<Void>
	                .then(Mono.fromRunnable(() -> sendFin(queryId, principal)));
	        })
	        .subscribe(
	            null,
	            err -> log.error("MAM Sync failed for room {} at cursor {}", roomId, currentAfterId, err)
	        );
	}

	/**
	 * Handles "Infinite Scroll" logic (loading older messages).
	 * Uses DESC order to find the N messages immediately preceding the 'beforeId'.
	 */
	private void loadBeforeId(ChannelHandlerContext ctx, String roomId, String beforeId, int maxResults, String queryId, XmppPrincipal principal) {
	    log.debug("MAM Request for Room {}: beforeId={}, max={}", roomId, beforeId, maxResults);

	    PageRequest pageRequest = PageRequest.of(0, maxResults);

	    // 1. Define the source Flux
	    Flux<MucMessage> messageFlux = (beforeId == null || beforeId.trim().isEmpty()) 
	            ? repository.findByRoomIdOrderByIdDesc(roomId, principal.getUserKey(), pageRequest)
	            : repository.findHistoricalMessages(roomId, beforeId, principal.getUserKey(), pageRequest);

	    messageFlux
	        .collectList()
	        .<Void>flatMap(list -> {
	            // 2. Secondary Java-side authorization filter
	            List<MucMessage> authorizedMessages = list.stream()
	                .filter(msg -> isAuthorized(msg, principal))
	                .toList();

	            // 3. RECURSION LOGIC: 
	            // If we found nothing authorized, but the DB still had data, 
	            // we must "walk" further back using the oldest ID in the current batch.
	            if (authorizedMessages.isEmpty() && !list.isEmpty()) {
	                String oldestIdInBatch = list.get(list.size() - 1).getId();
	                log.debug("No authorized messages in batch for room {}. Walking back from {}", roomId, oldestIdInBatch);
	                
	                return Mono.fromRunnable(() -> 
	                    loadBeforeId(ctx, roomId, oldestIdInBatch, maxResults, queryId, principal)
	                );
	            }

	            // 4. DISPATCH LOGIC: 
	            // Process the authorized batch (or handle the end of history if list was empty).
	            return Flux.fromIterable(authorizedMessages)
	                .concatMap(msg -> dispatchMamResult(msg, queryId, principal))
	                .then(Mono.fromRunnable(() -> sendFin(queryId, principal)));
	        })
	        .doOnError(e -> log.error("MAM failure for room {} at beforeId {}", roomId, beforeId, e))
	        .subscribe();
	}
	
	/**
	 * Filters messages to ensure Private Messages within a MUC are only visible to the recipient.
	 */
	private boolean isAuthorized(MucMessage msg, XmppPrincipal principal) {
		return !(msg.getHiddenFromUserKeys() != null && msg.getHiddenFromUserKeys().contains(principal.getUserKey()));
	}
	
	
	private boolean isPrincipalRecipient(MucMessage msg, XmppPrincipal principal) {
		return (msg.getTo() == null || msg.getTo().equalsIgnoreCase(principal.getUserKey()));
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
	
	public Mono<MucMessage> findByMessageId(String id) {
		return repository.findByMessageId(id);
	}
	
	public Mono<MucMessage> save(MucMessage message) {
		return repository.save(message);
	}
	
	public Mono<Void> hideMessageForUser(String messageId, String userKey) {
	    // 1. Locate the document by ID
	    Query query = new Query(Criteria.where("messageId").is(messageId));

	    // 2. Define the specific update (Atomic $addToSet)
	    Update update = new Update()
	    		.addToSet("hiddenFromUserKeys", userKey)
	    		.set("updateCursorId", UlidCreator.getMonotonicUlid().toLowerCase());

	    // 3. Execute 'updateFirst' to modify ONLY that field
	    return reactiveMongoTemplate.updateFirst(query, update, MucMessage.class)
	            .then();
	}
		
	/**
	 * Fetches and dispatches message updates (like retractions or view changes) that occurred 
	 * in a specific room after a given cursor point.
	 * 
	 * @param roomId    The unique identifier of the MUC room.
	 * @param afterId   The ULID cursor used to resume the update stream.
	 * @param principal The session context of the user requesting the updates.
	 */
	private void syncRecentRoomUpdates(String roomId, String afterId, XmppPrincipal principal) {
	    log.info("Syncing updates for Room {}: starting from cursor {}", roomId, afterId);

	    // 1. Query the repository for all message changes in this room newer than the provided ULID.
	    // OrderByIdAsc ensures we process and dispatch updates in the exact order they occurred.
	    repository.findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualOrderByIdAsc(roomId, afterId, afterId)
	        
	        // 2. Filter: Ensure the update is relevant to the requesting principal.
	        // This prevents leaking "Delete for Me" events or private stanzas to the wrong users.
	        .filter(msg -> isPrincipalRecipient(msg, principal))

	        // 3. Sequential Dispatch: Use concatMap to ensure stanzas are sent to the local 
	        // dispatcher in order. This maintains protocol consistency for the client.
	        .concatMap(msg -> dispatchRecentUpdatesResult(msg, afterId, principal))

	        // 4. Subscription: Since this is a void-returning fire-and-forget background task,
	        // we subscribe to trigger the reactive pipeline. 
	        // NOTE: In a production environment, consider adding error logging inside .subscribe().
	        .subscribe(
	            null, 
	            error -> log.error("Failed to sync updates for user {} in room {}: {}", 
	                                principal.getUserKey(), roomId, error.getMessage())
	        );
	}
	
	/**
	 * Processes a single message update and dispatches the appropriate XMPP stanza 
	 * if the update occurs after the client's current sync cursor (afterId).
	 * 
	 * @param msg       The message metadata containing deletion or visibility status.
	 * @param afterId   The ULID string representing the client's last synchronized state.
	 * @param principal The session context of the user receiving the update.
	 * @return A Mono<Void> that completes after the stanza is dispatched or skipped.
	 */
	private Mono<Void> dispatchRecentUpdatesResult(MucMessage msg, String afterId, XmppPrincipal principal) {

	    // Wrap XML generation in Mono.fromCallable to keep the logic within the reactive pipeline.
	    return Mono.fromCallable(() -> buildUpdateXml(msg, principal))
	            .flatMap(optionalXml -> optionalXml
	                .map(xml -> Mono.fromRunnable(() -> 
	                    // 3. Side-effect: Dispatch the generated stanza to the user's local connection.
	                    localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), xml)
	                ).then()) // Convert Runnable to Mono<Void>
	                .orElse(Mono.empty())
	            );
	}

	/**
	 * Strategy method to determine which type of synchronization stanza to generate.
	 * Returns an Optional.empty() if no update (retraction or hide) is required for this user.
	 */
	private Optional<String> buildUpdateXml(MucMessage msg, XmppPrincipal principal) {
	    // Priority 1: Global Retraction (XEP-0424). If deletedAt is set, everyone needs a retraction.
	    if (msg.getDeletedAt() != null) {
	        return Optional.of(buildRetractionXml(msg, principal));
	    } 
	    
	    // Priority 2: "Delete for Me" (View Management). Only notify if this specific user hidden the message.
	    if (msg.getHiddenFromUserKeys() != null && msg.getHiddenFromUserKeys().contains(principal.getUserKey())) {
	        return Optional.of(buildHideEventXml(msg, principal));
	    }

	    return Optional.empty();
	}

	/**
	 * Builds a XEP-0424 Message Retraction stanza for MUC groupchat.
	 */
	private String buildRetractionXml(MucMessage msg, XmppPrincipal principal) {
	    String timestamp = XmppStanzaUtil.formatTimestamp(msg.getDeletedAt());
	    // Construct the Occupant JID (room@service/nick)
	    String groupJid = jidUtil.getGroupBareJid(msg.getRoomId()) + "/" + msg.getFrom();

	    MessageRetractStanza retractStanza = MessageRetractStanza.builder()
	            .id(UUID.randomUUID().toString()) // Unique ID for this specific retraction stanza
	            .to(principal.getBareJid())       // Targeted at the user's bare JID
	            .from(groupJid)                  // Originating from the room occupant address
	            .by(groupJid)                    // The entity that performed the retraction
	            .retractedId(msg.getMessageId()) // The original 'id' of the message to be removed
	            .type(XmppMessageType.GROUPCHAT.getXmlValue())
	            .stamp(timestamp)
	            .build();

	    // Injects the server's tracking ID (cursor) into the <stanza-id/> element for MAM/Syncing.
	    return XmppStanzaUtil.insertStanzaId(retractStanza.toXml(), msg.getUpdateCursorId(), principal.getDomain());
	}

	/**
	 * Builds a custom View Management Sync stanza to synchronize "hidden" state across devices.
	 */
	private String buildHideEventXml(MucMessage msg, XmppPrincipal principal) {
		String xml = null;
		try {
			ViewManagementSyncStanza vmSync = ViewManagementSyncStanza.builder()
					.id(UUID.randomUUID().toString())
					.targetId(msg.getMessageId()) // The message ID that should be hidden from view
					.from(principal.getBareJid()) // Sent from the user's bare JID
					.room(jidUtil.getGroupBareJid(msg.getRoomId()))
					.to(principal.getBareJid())   // Sent to self to ensure all connected resources (phone, web) sync
					.build();
			// Generate a fresh monotonic ULID for the view management event itself.
			String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();

			// Inject the new ULID as the stanza-id so the client can update its sync cursor.
			xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), ulidString, principal.getDomain());
		} catch(Exception ex) {
			log.error("Error composing View management sync stanza ", ex);
		}
		return xml;
	}
}