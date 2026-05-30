package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.repository.MucRoomReadCursorRepository;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.stanza.MessageSyncReadReceiptStanza;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MamUtil;
import com.algomeet.xmpp.chatservice.util.XmppCustomStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

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
	private final GroupCacheService groupCacheService;
	private final XmppUtil xmppUtil;
	private final DomainProperties domainProperties;
	private final ReactiveMongoTemplate reactiveMongoTemplate;
	private final JidUtil jidUtil;
	private final MucRoomReadCursorRepository mucRoomReadCursorRepository;
	private final MamUtil mamUtil;

	/**
	 * Persists a room event (message or signaling) to the archive.
	 *
	 * @param xml        The raw XML stanza content.
	 * @param info       Metadata extracted from the stanza (ID, Category, Encryption status).
	 * @param toRoomId   The internal ID of the room destination.
	 * @param toMucMember The specific recipient (for private MUC messages) User Key.
	 * @param from       The sender's User Key.
	 * @param stanzaId The unique internal ID (UUIDv7) for database indexing.
	 * @return A {@link Mono} containing the saved {@link MucMessage}.
	 */
	public Mono<MucMessage> archiveEvent(String xml, String id, String toRoomId, String toMucMember, String from, UUID stanzaId, Boolean isCountable) {	
		UUID messageId = StringUtils.hasText(id) 
			    ? UUID.fromString(id) 
			    : UuidCreator.getTimeOrderedEpoch();
		
		MucMessage event = new MucMessage();
		event.setId(stanzaId);
		event.setMessageId(messageId);
		event.setRoomId(UUID.fromString(toRoomId));
		event.setFrom(UUID.fromString(from));
		event.setTo(StringUtils.hasText(toMucMember) ? UUID.fromString(toMucMember) : null);
		event.setCountable(isCountable);
		
		// Get target message ID for reactions and edits, they are not countable stanza.
		// sample stanza: <target xmlns='urn:algomeet:meta:0' id='019e537d-31a0-7556-a160-7ac448312343'/>
		String targetMessageId = !(isCountable) ? XmppCustomStanzaUtil.getTargetMessageId(xml) : null;
		event.setTargetMessageId(targetMessageId != null ? UUID.fromString(targetMessageId) : null);
		event.setStanzaXml(xml);

		return repository.save(event);
	}
	
	/**
	 * Persists a room event (message or signaling) to the archive.
	 *
	 * @param xml        The raw XML stanza content.
	 * @param info       Metadata extracted from the stanza (ID, Category, Encryption status).
	 * @param toRoomId   The internal ID of the room destination.
	 * @param toMucMember The specific recipient (for private MUC messages) User Key.
	 * @param from       The sender's User Key.
	 * @param stanzaId The unique internal ID (UUIDv7) for database indexing.
	 * @return A {@link Mono} containing the saved {@link MucMessage}.
	 */
	public Mono<MucMessage> archiveEvent(String xml, String id, String toRoomId, String toMucMember, String from, UUID stanzaId) {	
		UUID messageId = StringUtils.hasText(id) 
			    ? UUID.fromString(id) 
			    : UuidCreator.getTimeOrderedEpoch();
		
		MucMessage event = new MucMessage();
		event.setId(stanzaId);
		event.setMessageId(messageId);
		event.setRoomId(UUID.fromString(toRoomId));
		event.setFrom(UUID.fromString(from));
		event.setTo(StringUtils.hasText(toMucMember) ? UUID.fromString(toMucMember) : null);
		event.setCountable(XmppCustomStanzaUtil.isCountableMessage(xml));
		event.setStanzaXml(xml);

		return repository.save(event);
	}
	
	/**
	 * Routes MAM queries to the appropriate pagination handler based on RSM fields.
	 * * @param ctx       Netty context.
	 * @param roomId    Target MUC room.
	 * @param xml       The MAM <query/> stanza.
	 * @param principal User requesting the history.
	 */
	public void fetchMucArchive(ChannelHandlerContext ctx, UUID roomId, String xml, XmppPrincipal principal) {
		String afterId = XmppStanzaUtil.getFieldValue(xml, "after-id");
		String beforeId = XmppStanzaUtil.getFieldValue(xml, "before-id");
		int maxResults = XmppStanzaUtil.getRsmMax(xml, 50);
		String queryId = XmppStanzaUtil.getAttribute(xml, "id");

		// Get room details
		MucRoomDto room = groupCacheService.getCachedGroup(roomId.toString());

		if (room == null || !(room.getMembers().stream()
				.anyMatch(m -> principal.getUserKey().equalsIgnoreCase(m.getUserKey())))) {
			log.error("Unauthorized access to room {}", roomId);
			xmppUtil.sendError(ctx, queryId, principal.getBareJid(), domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
					XmppErrorConditions.INTERNAL_SERVER_ERROR, "Unauthorized access");
			return;
		}

		// Strategy: If 'after' is present, we move forward in time.
		// Otherwise (or if 'before' is present), we move backward into history.
		if (afterId != null) {
			if (!StringUtils.hasText(afterId)) {
				afterId = Constants.SMALLEST_UUID_V7.toString();
			}
			// Synchronize the current conversation starting point across local devices.
			syncRoomDeletedMessages(roomId, principal).subscribe();
			
			// Sync recent updates
//			syncRoomRecentUpdates(roomId, UUID.fromString(afterId), principal);

			loadAfterWithRetry(roomId, UUID.fromString(afterId), principal, queryId, maxResults);
		} else {
			if (!StringUtils.hasText(beforeId)) {
				beforeId = UuidCreator.getTimeOrderedEpoch().toString();
			}
			
			loadBeforeIdWithRetry(ctx, roomId, UUID.fromString(beforeId), maxResults, queryId, principal);
		}
	}

	/**
	 * Handles "Load Newer" or "Sync from last disconnect" logic.
	 * Uses ASC order to stream messages from the oldest in the set to the newest.
	 */
	private void loadAfterWithRetry(UUID roomId, UUID currentAfterId, XmppPrincipal principal, String queryId, int maxResults) {
		Pageable pageRequest = PageRequest.of(0, maxResults);

		// Used for delete group chat conversation for a particular user
		Instant historyCutoff = Instant.EPOCH;
		repository.findByRoomIdAndIdGreaterThanAndToIsNullOrEqualtoUserkeyAndNotHiddenOrderByIdAsc(
				roomId, currentAfterId, UUID.fromString(principal.getUserKey()), historyCutoff, pageRequest)
		.collectList()
		// Explicitly define the generic type <Void> for flatMap
		.<Void>flatMap(list -> {

			List<MucMessage> authorizedMessages = list.stream()
					.filter(msg -> MamUtil.isAuthorized(msg, principal))
					.collect(Collectors.toList());

			// 1. RE-QUERY LOGIC: Empty authorized list but DB had data
			if (authorizedMessages.isEmpty() && !list.isEmpty()) {
				UUID newAfterId = list.get(list.size() - 1).getId();
				log.debug("No authorized messages in batch. Re-querying from {}", newAfterId);

				// Use fromRunnable to return Mono<Void> and trigger the next hop
				return Mono.fromRunnable(() -> 
				loadAfterWithRetry(roomId, newAfterId, principal, queryId, maxResults)
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
	private void loadBeforeIdWithRetry(ChannelHandlerContext ctx, UUID roomId, UUID beforeId, int maxResults, String queryId, XmppPrincipal principal) {
		log.debug("MAM Request for Room {}: beforeId={}, max={}", roomId, beforeId, maxResults);

		PageRequest pageRequest = PageRequest.of(0, maxResults);

		// Used for delete group chat conversation for a particular user
		Instant historyCutoff = Instant.EPOCH;
		// 1. Define the source Flux
		Flux<MucMessage> messageFlux = repository.findByRoomIdAndIdLessThanAndToIsNullOrEqualtoUserkeyAndNotHiddenOrderByIdDesc(
				roomId, 
				beforeId, 
				UUID.fromString(principal.getUserKey()), 
				historyCutoff,
				pageRequest);

		messageFlux
		.collectList()
		.<Void>flatMap(list -> {
			// 2. Secondary Java-side authorization filter
			List<MucMessage> authorizedMessages = list.stream()
					.filter(msg -> MamUtil.isAuthorized(msg, principal))
					.toList();

			// 3. RECURSION LOGIC: 
			// If we found nothing authorized, but the DB still had data, 
			// we must "walk" further back using the oldest ID in the current batch.
			if (authorizedMessages.isEmpty() && !list.isEmpty()) {
				UUID oldestIdInBatch = list.get(list.size() - 1).getId();
				log.debug("No authorized messages in batch for room {}. Walking back from {}", roomId, oldestIdInBatch);

				return Mono.fromRunnable(() -> 
				loadBeforeIdWithRetry(ctx, roomId, oldestIdInBatch, maxResults, queryId, principal)
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
	 * Wraps a database record into a XEP-0313 <result/> stanza and dispatches it.
	 */
	private Mono<Void> dispatchMamResult(MucMessage msg, String queryId, XmppPrincipal principal) {
	    // Determine the timestamp (Format: 2026-05-16T19:45:43Z)
	    String timestamp = XmppStanzaUtil.formatTimestamp(msg.getCreatedAt()); 
	    
	    // 1. Fetch all participants in this room who have read past this message's ID threshold
	    return mucRoomReadCursorRepository.findByRoomIdAndLastReadSidGreaterThanEqual(msg.getRoomId(), msg.getId())
	            .map(rc -> rc.getRoomId().toString()) // Extract user keys
	            .collectList()                      // Accumulate reactive items into a List<String>
	            .flatMap(userKeys -> {              // Shift into the template string construction logic
	                // 2. Generate the dynamic XML reader tags block from your collected list
	                String readersXmlBlock = MamUtil.buildReadersBlock(userKeys);

	                // 3. Construct the comprehensive MAM result stanza package
	                String queryIdAttr = (queryId != null && !queryId.isBlank()) ? "queryid='" + queryId + "' " : "";
	                
	                String mamResult = String.format(
	                        "<message to='%s'>" +
	                            "<result xmlns='urn:xmpp:mam:2' %sid='%s'>" +
	                                "<forwarded xmlns='urn:xmpp:forward:0'>" +
	                                    "<delay xmlns='urn:xmpp:delay' stamp='%s'/>" +
	                                    "%s" + 
	                                "</forwarded>" +
	                                "<read-receipts xmlns='urn:algomeet:xmpp:read:0'>" +
	                                    "%s" + 
	                                "</read-receipts>" +
	                            "</result>" +
	                        "</message>",
	                        principal.getBareJid(),
	                        queryIdAttr,
	                        msg.getId(),
	                        timestamp,
	                        mamUtil.convertToMamFormat(msg.getFrom().toString(), msg.getRoomId().toString(), msg.getStanzaXml()),
	                        readersXmlBlock
	                );        

	                // 4. Dispatch the stanza asynchronously within the reactive execution flow
	                return Mono.<Void>fromRunnable(() -> 
	                    localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), mamResult).subscribe()
	                );
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
		localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), fin)
		.subscribe();
	}

	public Mono<MucMessage> findByMessageId(UUID id) {
		return repository.findFirstByMessageId(id);
	}

	public Mono<MucMessage> save(MucMessage message) {
		return repository.save(message);
	}

	public Mono<Void> hideMessageForUser(UUID messageId, UUID userKey) {
		// 1. Locate the document by ID
		Query query = new Query(Criteria.where("messageId").is(messageId));

		// 2. Define the specific update (Atomic $addToSet)
		Update update = new Update()
				.addToSet("hiddenFromUserKeys", userKey)
				.set("updateCursorId", UuidCreator.getTimeOrderedEpoch());

		// 3. Execute 'updateFirst' to modify ONLY that field
		return reactiveMongoTemplate.updateFirst(query, update, MucMessage.class)
				.then();
	}
	
	/**
	 * Fetches and dispatches message updates (like retractions or view changes) that occurred 
	 * in a specific room after a given cursor point.
	 * 
	 * @param roomId    The unique identifier of the MUC room.
	 * @param afterId   The UUIDv7 cursor used to resume the update stream.
	 * @param principal The session context of the user requesting the updates.
	 */
//	private void syncRoomRecentUpdates(UUID roomId, UUID afterId, XmppPrincipal principal) {
//		log.info("Syncing updates for Room {}: starting from cursor {}", roomId, afterId);
//
//		// 1. Query the repository for all message changes in this room newer than the provided ULUUIDv7ID.
//		// OrderByIdAsc ensures we process and dispatch updates in the exact order they occurred.
//		Pageable pageable = PageRequest.of(0, 10000);
//		repository.findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualOrderByIdAsc(roomId, afterId, afterId, pageable)
//
//		// 2. Filter: Ensure the update is relevant to the requesting principal.
//		// This prevents leaking "Delete for Me" events or private stanzas to the wrong users.
//		.filter(msg -> MamUtil.isPrincipalRecipient(msg, principal))
//
//		// 3. Sequential Dispatch: Use concatMap to ensure stanzas are sent to the local 
//		// dispatcher in order. This maintains protocol consistency for the client.
//		.concatMap(msg -> dispatchRecentUpdatesResult(msg, principal))
//
//		// 4. Subscription: Since this is a void-returning fire-and-forget background task,
//		// we subscribe to trigger the reactive pipeline. 
//		// NOTE: In a production environment, consider adding error logging inside .subscribe().
//		.subscribe(
//				null, 
//				error -> log.error("Failed to sync updates for user {} in room {}: {}", 
//						principal.getUserKey(), roomId, error.getMessage(), error)
//				);
//	}
	
	private Mono<Void> syncRoomDeletedMessages(UUID roomId, XmppPrincipal principal) {
	    log.info("Syncing deletes for Room {}", roomId);

	    Instant historyCutoff = Instant.EPOCH;
	    
	    return repository
	            .findFirstByRoomIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(roomId, historyCutoff)
	            .switchIfEmpty(Mono.defer(() -> {
	                log.info("No messages found in room {}", roomId);
	                
	                MucMessage emptyAnchorMsg = new MucMessage();
	                emptyAnchorMsg.setRoomId(roomId);
	                emptyAnchorMsg.setId(Constants.NIL_UUID); 
	                emptyAnchorMsg.setStartOfRoomConversation(true);
	                
	                // FIX: Execute the dispatch, then return the anchor object to satisfy the Mono<MucMessage> type restriction
	                return dispatchRecentUpdatesResult(emptyAnchorMsg, principal)
	                        .thenReturn(emptyAnchorMsg); 
	            }))
	            .flatMap(msg -> { 
	                msg.setStartOfRoomConversation(true);
	                return dispatchRecentUpdatesResult(msg, principal);
	            })
	            .then(); // Safely squashes the final stream back into Mono<Void> for the method signature
	}

	/**
	 * Processes and dispatches recent state mutations for a MUC message to a user's active session.
	 * 
	 * <p>Asynchronously evaluates message delta priorities (retractions, visibility updates, 
	 * read-receipt syncs) and pushes the resulting structural XML stanza downstream if a 
	 * state variance is detected.</p>
	 *
	 * @param msg       The mutated MUC message metadata document.
	 * @param principal The active user context target receiving the synchronization event.
	 * @return A {@link Mono<Void>} that completes when the state evaluation and physical 
	 *         delivery dispatch loops finish processing.
	 */
	private Mono<Void> dispatchRecentUpdatesResult(MucMessage msg, XmppPrincipal principal) {
	    // 1. Invoke the updated non-blocking XML generation pipeline directly
	    return buildUpdateXml(msg, principal)
	            .flatMap(optionalXml -> {
	                // 2. Short-circuit immediately if no matching delta update state was found
	                if (optionalXml.isEmpty()) {
	                    return Mono.empty();
	                }

	                String xmlStanza = optionalXml.get();

	                // 3. Side-effect: Asynchronously route the built structural XML payload to the local session
	                return Mono.fromRunnable(() -> 
	                    localStanzaDispatcher.dispatchLocally(
	                        principal.getUserKey(), 
	                        principal.getUserKey(), 
	                        xmlStanza
	                    ).subscribe()
	                );
	            });
	}

	/**
	 * Evaluates message delta state priorities to dynamically assemble structural mutation XML.
	 * 
	 * <p>Prioritizes fast-path synchronous metadata evaluations (conversational shifts, 
	 * XEP-0424 retractions, visibility management) before falling back to the 
	 * asynchronous database-backed read receipt synchronization engine.</p>
	 *
	 * @param msg       The target MUC message document containing state flags.
	 * @param principal The active user session executing or receiving this update synchronization.
	 * @return A {@link Mono} emitting an {@link Optional} containing the serialized XML payload,
	 *         or completing empty if no payload matches the state evaluation.
	 */
	private Mono<Optional<String>> buildUpdateXml(MucMessage msg, XmppPrincipal principal) {
	    
	    // Priority 1: Clear tracking boundary adjustments (Conversational resets across devices)
	    if (Boolean.TRUE.equals(msg.getStartOfRoomConversation())) {
	        return Mono.just(Optional.of(mamUtil.buildSyncConversationXml(msg, principal)));
	    }
	    
	    // Priority 2: Global Message Retractions (XEP-0424 structural execution)
	    if (msg.getDeletedAt() != null) {
	        return Mono.just(Optional.of(mamUtil.buildRetractionXml(msg, principal)));
	    } 

	    // Priority 3: Localized "Delete for Me" / Visibility toggles
	    if (msg.getHiddenFromUserKeys() != null && msg.getHiddenFromUserKeys().contains(principal.getUserKey())) {
	        return Mono.just(Optional.of(mamUtil.buildHideEventXml(msg, principal)));
	    }			

	    // Priority 4: Fallback to async read receipt synchronization (Asynchronous collection engine)
	    return buildSyncReadReceiptsXml(msg, principal)
	            .map(Optional::of)
	            .defaultIfEmpty(Optional.empty());
	}
	
	private Mono<String> buildSyncReadReceiptsXml(MucMessage msg, XmppPrincipal principal) {
	    // 1. Fetch all participants in this room who have read past this message's ID threshold
	    return mucRoomReadCursorRepository.findByRoomIdAndLastReadSidGreaterThanEqual(msg.getRoomId(), msg.getId())
	            .map(rc -> rc.getRoomId().toString()) // Extract user keys
	            .collectList()                      // Accumulate reactive items into a List<String>
	            .map(userKeys -> {                  // Use .map() since we return a synchronous String from this block
	                
	                // Construct the group bare JID (room@service).
	                String groupJid = jidUtil.getGroupBareJid(msg.getRoomId().toString());
	                
	                // 2. Build the synchronization stanza using the collected user keys
	                MessageSyncReadReceiptStanza syncConversationStanza = MessageSyncReadReceiptStanza.builder()
	                        // Unique ID for this synchronization stanza.
	                        .id(UuidCreator.getTimeOrderedEpoch().toString())

	                        // The stanza originates from the group room.
	                        .from(groupJid)

	                        // Contains the collection of readers who have acknowledged this message.
	                        .readerUserKeys(userKeys)
	                        
	                        // The explicit target message ID threshold anchoring this status sync.
	                        .targetMessageId(msg.getId().toString())

	                        // Group chat message type.
	                        .type(XmppMessageType.GROUPCHAT.getXmlValue())

	                        .build();

	                // 3. Serialize the synchronization stanza into XML format.
	                return syncConversationStanza.toXml();
	            });
	}	
}