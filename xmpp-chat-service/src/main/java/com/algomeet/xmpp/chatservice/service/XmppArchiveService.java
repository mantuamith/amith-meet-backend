package com.algomeet.xmpp.chatservice.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.document.MucRoomReadCursor;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.repository.MucRoomReadCursorRepository;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.stanza.MessageRetractStanza;
import com.algomeet.xmpp.chatservice.stanza.MessageSyncConversationStanza;
import com.algomeet.xmpp.chatservice.stanza.MessageSyncReadReceiptStanza;
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
	// Pre-compile patterns outside the method to save CPU
	private static final Pattern TO_ATTR_PATTERN = Pattern.compile("\\s+to='[^']*'");
	private static final Pattern FROM_ATTR_PATTERN = Pattern.compile("from='[^']*'");
	
	private final MucMessageRepository repository;
	private final LocalStanzaDispatcher localStanzaDispatcher;
	private final GroupCacheService groupCacheService;
	private final XmppUtil xmppUtil;
	private final DomainProperties domainProperties;
	private final ReactiveMongoTemplate reactiveMongoTemplate;
	private final JidUtil jidUtil;
	private final MucRoomReadCursorRepository mucRoomReadCursorRepository;

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
		MucMessage event = new MucMessage();
		event.setId(internalId);
		event.setMessageId(UUID.fromString(info.getMessageId()));
		event.setRoomId(toRoomId);
		event.setFrom(from);
		event.setTo(toMucMember);
		event.setStanzaXml(xml);
		event.setCategory(info.getCategory());
		event.setRefersTo(info.getTargetId());
		event.setE2EE(info.isE2EE());

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
		if (afterId != null) {
			// Synchronize the current conversation starting point across local devices.
			syncRoomDeletedMessages(roomId, principal);
			
			// Sync recent updates
			syncRoomRecentUpdates(roomId, afterId, principal);

			loadAfterWithRetry(roomId, afterId, principal, queryId, maxResults);
		} else {
			loadBeforeIdWithRetry(ctx, roomId, beforeId, maxResults, queryId, principal);
		}
	}

	/**
	 * Handles "Load Newer" or "Sync from last disconnect" logic.
	 * Uses ASC order to stream messages from the oldest in the set to the newest.
	 */
	private void loadAfterWithRetry(String roomId, String currentAfterId, XmppPrincipal principal, String queryId, int maxResults) {
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
	private void loadBeforeIdWithRetry(ChannelHandlerContext ctx, String roomId, String beforeId, int maxResults, String queryId, XmppPrincipal principal) {
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
	    // Determine the timestamp (Format: 2026-05-16T19:45:43Z)
	    String timestamp = XmppStanzaUtil.formatTimestamp(msg.getCreatedAt()); 
	    
	    // 1. Fetch all participants in this room who have read past this message's ID threshold
	    return mucRoomReadCursorRepository.findByRoomIdAndLastReadMidGreaterThanEqual(msg.getRoomId(), msg.getMessageId())
	            .map(MucRoomReadCursor::getUserKey) // Extract user keys
	            .collectList()                      // Accumulate reactive items into a List<String>
	            .flatMap(userKeys -> {              // Shift into the template string construction logic
	                // 2. Generate the dynamic XML reader tags block from your collected list
	                String readersXmlBlock = buildReadersBlock(userKeys);

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
	                        convertToMamFormat(msg.getFrom(), msg.getRoomId(), msg.getStanzaXml()),
	                        readersXmlBlock
	                );        

	                // 4. Dispatch the stanza asynchronously within the reactive execution flow
	                return Mono.<Void>fromRunnable(() -> 
	                    localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), mamResult)
	                );
	            });
	}
	
	/**
	 * Constructs a highly optimized XML string block containing multiple reader entries 
	 * from a list of user keys.
	 *
	 * @param userKeys A list of unique participant user keys who have read the message.
	 * @return A joined XML string fragment of empty elements, or an empty string if the list is empty.
	 */
	public static String buildReadersBlock(List<String> userKeys) {
	    if (userKeys == null || userKeys.isEmpty()) {
	        return "";
	    }

	    
	    // Pre-allocate buffer capacity to avoid mid-stream array copies 
	    // (Estimate ~65 characters per reader tag block)
	    StringBuilder xmlBuilder = new StringBuilder(userKeys.size() * 65);
	    
	    for (String userKey : userKeys) {
	        if (userKey != null && !userKey.isBlank()) {
	            xmlBuilder.append("<reader user-key='")
	                      .append(userKey)
	                      .append("'/>");
	        }
	    }
	    
	    return xmlBuilder.toString();
	}
	
	private String convertToMamFormat(String fromUserKey, String toRoomId, String msg) {
		/**
		 * Example raw group chat message stanza:
		 *
		 * <message from='2fc35cae-e0b7-40a5-b2aa-e86206730e99@algomeet.app'
		 *          to='289c5f4d-58a0-4def-bf5b-0fd15c045575@conference.algomeet.app'
		 *          type='groupchat'
		 *          id='msg-algomeet-1321199'>
		 *     <body>
		 *         Team, the Netty server is now handling Jingle stanzas correctly!
		 *     </body>
		 *     <stanza-id xmlns='urn:xmpp:sid:0'
		 *                by='algomeet.app'
		 *                id='01kqf1ty089crppav99f5nr50v'/>
		 * </message>
		 */

		int headerEnd = msg.indexOf('>') + 1;
		if (headerEnd <= 0) return msg;

		String header = msg.substring(0, headerEnd);

		if (header.indexOf("\"") != -1) {
			header = header.replaceAll("\"", "'");
		}

		// 1. Remove 'to'
		header = TO_ATTR_PATTERN.matcher(header).replaceAll("");

		// 2. Replace 'from'
		String newFrom = "from='" + jidUtil.getGroupBareJid(toRoomId) + "/" + fromUserKey + "'";
		header = FROM_ATTR_PATTERN.matcher(header).replaceAll(newFrom);

		// 3. Rebuild using StringBuilder to minimize object copies
		return new StringBuilder(header.length() + msg.length() - headerEnd)
				.append(header)
				.append(msg, headerEnd, msg.length())
				.toString();
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

	public Mono<Void> hideMessageForUser(UUID messageId, String userKey) {
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
	private void syncRoomRecentUpdates(String roomId, String afterId, XmppPrincipal principal) {
		log.info("Syncing updates for Room {}: starting from cursor {}", roomId, afterId);

		// 1. Query the repository for all message changes in this room newer than the provided ULID.
		// OrderByIdAsc ensures we process and dispatch updates in the exact order they occurred.
		repository.findByRoomIdAndUpdateCursorIdGreaterThanAndIdLessThanEqualOrderByIdAsc(roomId, afterId, afterId)

		// 2. Filter: Ensure the update is relevant to the requesting principal.
		// This prevents leaking "Delete for Me" events or private stanzas to the wrong users.
		.filter(msg -> isPrincipalRecipient(msg, principal))

		// 3. Sequential Dispatch: Use concatMap to ensure stanzas are sent to the local 
		// dispatcher in order. This maintains protocol consistency for the client.
		.concatMap(msg -> dispatchRecentUpdatesResult(msg, principal))

		// 4. Subscription: Since this is a void-returning fire-and-forget background task,
		// we subscribe to trigger the reactive pipeline. 
		// NOTE: In a production environment, consider adding error logging inside .subscribe().
		.subscribe(
				null, 
				error -> log.error("Failed to sync updates for user {} in room {}: {}", 
						principal.getUserKey(), roomId, error.getMessage(), error)
				);
	}
	
	private void syncRoomDeletedMessages(String roomId, XmppPrincipal principal) {
		log.info("Syncing deletes for Room {}", roomId);

		repository.findFirstByRoomIdOrderByIdAsc(roomId)
	    .switchIfEmpty(Mono.defer(() -> {
	        log.info("No messages found in room");
	        // Explicitly instantiate 'MucMessage'
	        MucMessage msg = new MucMessage();
	        msg.setRoomId(roomId);
	        msg.setId(Constants.EMPTY_CONVERSATION_STANZA_ID); // indicator of empty room conversation
	        msg.setStartOfRoomConversation(true);
	        // pass an empty message
	        dispatchRecentUpdatesResult(msg, principal).subscribe();
	        
	        return Mono.empty();
	    }))
	    .flatMap(msg -> { // Explicitly typed 'MucMessage'
	        msg.setStartOfRoomConversation(true);
	        return dispatchRecentUpdatesResult(msg, principal);
	    })
	    .subscribe();
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
	                    )
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
	        return Mono.just(Optional.of(buildSyncConversationXml(msg, principal)));
	    }
	    
	    // Priority 2: Global Message Retractions (XEP-0424 structural execution)
	    if (msg.getDeletedAt() != null) {
	        return Mono.just(Optional.of(buildRetractionXml(msg, principal)));
	    } 

	    // Priority 3: Localized "Delete for Me" / Visibility toggles
	    if (msg.getHiddenFromUserKeys() != null && msg.getHiddenFromUserKeys().contains(principal.getUserKey())) {
	        return Mono.just(Optional.of(buildHideEventXml(msg, principal)));
	    }			

	    // Priority 4: Fallback to async read receipt synchronization (Asynchronous collection engine)
	    return buildSyncReadReceiptsXml(msg, principal)
	            .map(Optional::of)
	            .defaultIfEmpty(Optional.empty());
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
				.from(groupJid)                  // Originating from the room occupant address
				.by(groupJid)                    // The entity that performed the retraction
				.retractedId(msg.getMessageId().toString()) // The original 'id' of the message to be removed
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
					.targetId(msg.getMessageId().toString()) // The message ID that should be hidden from view
					.from(principal.getBareJid()) // Sent from the user's bare JID
					.room(jidUtil.getGroupBareJid(msg.getRoomId()))
					// Removed to attribute to shorten the message
					//.to(principal.getBareJid())   // Sent to self to ensure all connected resources (phone, web) sync
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
	
	/**
	 * Builds an XMPP synchronization stanza used to notify client devices
	 * about the current start of the group conversation.
	 *
	 * <p>
	 * The provided stanzaId represents the earliest remaining message in the
	 * conversation. Any messages before this stanzaId should be treated as
	 * permanently deleted and removed from local device storage.
	 * </p>
	 *
	 * @param msg the current first available group message
	 * @param principal the authenticated XMPP principal
	 * @return XML representation of the conversation synchronization stanza
	 */
	private String buildSyncConversationXml(MucMessage msg, XmppPrincipal principal) {

	    // Construct the group bare JID (room@service).
	    String groupJid = jidUtil.getGroupBareJid(msg.getRoomId());
	    MessageSyncConversationStanza syncConversationStanza = MessageSyncConversationStanza.builder()

	            // Unique ID for this synchronization stanza.
	            .id(UUID.randomUUID().toString())

	            // The stanza originates from the group room.
	            .from(groupJid)

	            // Indicates the current starting point of the conversation.
	            // All messages before this stanzaId are considered hard deleted.
	            .startOfConversationStanzaId(msg.getId())

	            // Group chat message type.
	            .type(XmppMessageType.GROUPCHAT.getXmlValue())

	            .build();

	    // Serialize the synchronization stanza into XML format.
	    return syncConversationStanza.toXml();
	}
	
	private Mono<String> buildSyncReadReceiptsXml(MucMessage msg, XmppPrincipal principal) {
	    // 1. Fetch all participants in this room who have read past this message's ID threshold
	    return mucRoomReadCursorRepository.findByRoomIdAndLastReadMidGreaterThanEqual(msg.getRoomId(), msg.getMessageId())
	            .map(MucRoomReadCursor::getUserKey) // Extract user keys
	            .collectList()                      // Accumulate reactive items into a List<String>
	            .map(userKeys -> {                  // Use .map() since we return a synchronous String from this block
	                
	                // Construct the group bare JID (room@service).
	                String groupJid = jidUtil.getGroupBareJid(msg.getRoomId());
	                
	                // 2. Build the synchronization stanza using the collected user keys
	                MessageSyncReadReceiptStanza syncConversationStanza = MessageSyncReadReceiptStanza.builder()
	                        // Unique ID for this synchronization stanza.
	                        .id(UUID.randomUUID().toString())

	                        // The stanza originates from the group room.
	                        .from(groupJid)

	                        // Contains the collection of readers who have acknowledged this message.
	                        .readerUserKeys(userKeys)
	                        
	                        // The explicit target message ID threshold anchoring this status sync.
	                        .targetMessageId(msg.getId())

	                        // Group chat message type.
	                        .type(XmppMessageType.GROUPCHAT.getXmlValue())

	                        .build();

	                // 3. Serialize the synchronization stanza into XML format.
	                return syncConversationStanza.toXml();
	            });
	}
		
	/**
	 * Advances the message synchronization cursor to indicate that the message has
	 * been acknowledged via a Read Receipt (Read ACK).
	 *
	 * <p>This method is used as a lightweight state marker for read tracking in
	 * distributed chat synchronization flows. When a client sends a Read ACK for
	 * a message, this operation updates the {@code updateCursorId} field with a
	 * new monotonic ULID to reflect that the message has been processed and read
	 * by the recipient.</p>
	 *
	 * <p>This cursor is primarily used for:
	 * <ul>
	 *   <li>Cross-device read state synchronization</li>
	 *   <li>Incremental MAM / delta polling</li>
	 *   <li>Efficient batch read acknowledgment tracking</li>
	 * </ul>
	 * </p>
	 *
	 * @param messageId The unique identifier of the message that has been read.
	 * @return A {@link Mono<Void>} signaling asynchronous completion of the update operation.
	 */
	public Mono<Void> advanceMessageSyncCursor(UUID messageId) {

	    // 1. Locate the document by message ID
	    Query query = new Query(Criteria.where("messageId").is(messageId));

	    // 2. Update cursor to a new monotonic ULID to mark Read ACK progression
	    Update update = new Update()
	            .set("updateCursorId", UlidCreator.getMonotonicUlid().toLowerCase());

	    // 3. Apply atomic update to ensure consistency in concurrent environments
	    return reactiveMongoTemplate.updateFirst(query, update, MucMessage.class)
	            .then();
	}
}