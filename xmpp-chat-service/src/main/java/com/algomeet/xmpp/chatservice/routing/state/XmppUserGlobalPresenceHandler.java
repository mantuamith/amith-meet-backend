package com.algomeet.xmpp.chatservice.routing.state;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.parser.StateStanzaParser;
import com.algomeet.xmpp.chatservice.routing.chat.OfflineMessageHandler;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.service.SmBufferMessageService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Schedulers;

/**
 * <p>Maintains global reachability and coordinates session activation.</p>
 * * <p>This handler acts as the <b>Presence Orchestrator</b>. When a user sends their 
 * initial availability, this component triggers a multi-stage activation process:</p>
 * <ul>
 * <li><b>Outbound:</b> Broadcasts the user's status to their roster and groups.</li>
 * <li><b>Inbound:</b> Pushes the current presence of all contacts back to the user.</li>
 * <li><b>Persistence:</b> Flushes stored offline messages to the active stream.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppUserGlobalPresenceHandler {

	private final UserSessionRegistry userSessionRegistry;
	private final OfflineMessageHandler offlineMessageHandler;
	private final XmppBroadcastUserPresenceHandler xmppUserGlobalPresenceHandler;
	private final XmppPresencePushHandler xmppPresencePushHandler;
	private final SmBufferMessageService smBufferMessageService;
	private final LocalStanzaDispatcher localStanzaDispatcher;
	
	/**
	 * Processes inbound presence stanzas to update user session state and trigger
	 * required synchronization workflows.
	 *
	 * Presence handling is a core part of XMPP session lifecycle management and is
	 * responsible for:
	 * - updating online/offline status across the cluster
	 * - broadcasting availability changes
	 * - triggering initial session sync (contacts, groups, offline messages)
	 * - handling Stream Management (XEP-0198) resumption recovery
	 *
	 * @param ctx       Netty channel context for the active connection
	 * @param principal authenticated user/session identity
	 * @param xml       raw XMPP presence stanza
	 */
	public void processPresence(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
		// RFC 6121: Only process "self-broadcast" presence directed to server
		// (e.g., <presence/> or initial availability updates)
		if (isSelfBroadcastPresence(xml)) {

			// Determine user availability state from presence stanza
			// (e.g., ONLINE, AWAY, DND, CHAT, UNAVAILABLE)
			UserState newState = determineState(xml);
			if (newState == null) return;

			// 1. Update distributed session registry so other cluster nodes
			// can correctly reflect this user's availability
			userSessionRegistry.updateSessionStatus(
					principal.getUserKey(),
					principal.getSessionId(),
					newState
					)
			// 2. Broadcast updated presence to contacts / subscribers
			// (XMPP presence fan-out mechanism)
			.then(xmppUserGlobalPresenceHandler.broadcastUserPresence(
					ctx,
					principal,
					newState
					))
			.subscribe();

			// 3. Ensure "initial session sync" logic executes only once per connection
			Attribute<Boolean> initialPresenceAttr =
					ctx.channel().attr(XmppSessionAttributes.IS_INITIAL_PRESENCE_SENT);

			if (initialPresenceAttr.get() == null || !initialPresenceAttr.get()) {

				// Check whether this session was successfully resumed via SM (XEP-0198)
				AtomicBoolean smResumptionSuccess =
						ctx.channel()
						.attr(XmppSessionAttributes.SM_RESUMPTION_SUCCESS_KEY)
						.get();

				// A. Push contact presence snapshot ("world state")
				// Only needed for fresh sessions (not fully resumed ones)
				if (smResumptionSuccess == null || !smResumptionSuccess.get()) {
					xmppPresencePushHandler.pushUsersPresence(ctx, principal)
					.subscribe();
				}

				// B. Deliver offline messages accumulated while user was disconnected
				// FIX: Safe-route the pipeline onto elastic processing pools to guarantee Netty loop non-blocking behavior
				offlineMessageHandler.deliverOfflineMessages(principal.getUserKey())
						.subscribeOn(Schedulers.boundedElastic())
						.doOnError(e -> log.error("Offline message delivery failed for user userKey={}", principal.getUserKey(), e))
						.subscribe();

				// C. Deliver buffered SM stanzas if session was successfully resumed
				if (smResumptionSuccess != null && smResumptionSuccess.get()) {
					deliverBufferStanzas(ctx, principal);
				}

				// Mark initial sync as completed to prevent duplicate execution
				initialPresenceAttr.set(true);

				log.info(
						"Session activation complete for {}. Presence sync and offline recovery executed.",
						principal.getUserKey()
						);
			}
		}
	}

	/**
	 * Detects if a presence stanza is a self-broadcast request.
	 * <p>According to RFC 6121, a presence stanza with no 'to' attribute 
	 * is a signal to the server to broadcast the user's availability 
	 * to all subscribed entities (the roster) and joined MUCs.</p>
	 */
	public static boolean isSelfBroadcastPresence(String xml) {
		if (xml == null) return false;

		int firstTag = xml.indexOf('<');
		if (firstTag == -1) return false;

		// Check if the root element is <presence
		if (XmppStanzaUtil.isPresenceStanza(xml)) {
			// A self-broadcast MUST NOT have a 'to' destination.
			// Presence with a 'to' attribute is a directed presence (XEP-0045) or subscription request.
			return !xml.contains(" to=") && !xml.contains(" to='");
		}

		return false;
	}

	/**
	 * Extracts the UserState enum from the raw XML stanza.
	 */
	private UserState determineState(String xml) {
		try {
			if (XmppStanzaUtil.isPresenceStanza(xml)) {
				return StateStanzaParser.determineState(xml);
			}
		} catch(Exception ex) {
			log.warn("Failed to parse presence state for stanza: {}", xml);
		}
		return null;
	}

	/**
	 * Delivers buffered XMPP stanzas to a client after session resumption.
	 *
	 * This method is triggered when a user reconnects with Stream Management (XEP-0198)
	 * enabled. It replays previously unacknowledged stanzas stored in the buffer.
	 *
	 * Responsibilities:
	 * - Fetch buffered stanzas for the SM session
	 * - Replay them in deterministic order
	 * - Push them through the local delivery pipeline (Netty/WebSocket)
	 *
	 * Note:
	 * - Delivery is asynchronous (reactive stream)
	 * - Execution is triggered via subscribe()
	 * - This does NOT block the Netty event loop
	 *
	 * @param ctx Netty channel context of the resumed session
	 * @param principal authenticated XMPP user session
	 */
	public void deliverBufferStanzas(ChannelHandlerContext ctx, XmppPrincipal principal) {
		String userKey = principal.getUserKey();

		// SM session identifier used to retrieve buffered stanzas
		// (must match XEP-0198 session resumption identifier - 'previd')
		String smSessionId =
				ctx.channel().attr(XmppSessionAttributes.SM_ID_KEY).get();

		if (smSessionId == null) {
			log.warn("Skipping stanza buffer delivery: SM_ID_KEY missing from session context attributes");
			return;
		}

		// Retrieve all buffered stanzas for this SM session
		// FIX: Replaced unordered, racing nested .subscribe loops inside doOnNext with clean flatMapSequential configurations
		smBufferMessageService.getStanzasForResumption(UUID.fromString(smSessionId))
				// For each buffered stanza, immediately dispatch it to the client
				// Replay stanza through local routing layer
				// This ensures consistent delivery semantics (same path as live messages)
				.flatMapSequential(msg -> localStanzaDispatcher.dispatchLocally(
						userKey,
						userKey,
						msg.getStanzaXml()
						))
				.subscribeOn(Schedulers.boundedElastic())
				// Collect items to ensure the buffer is cleared ONLY after all messages fully pass the delivery routing architecture
				.collectList()
				// Called when all buffered stanzas have been successfully replayed
				// Clean up buffer
				.flatMap(completedList -> smBufferMessageService.clearBuffer(UUID.fromString(smSessionId)))
				.doOnSuccess(v -> log.info("Completed offline/SM buffer delivery for user: {}", userKey))
				// Handles unexpected errors during replay (DB, routing, serialization, etc.)
				.doOnError(e ->
						log.error("Failed to deliver buffered stanzas for user {}: {}",
								userKey,
								e.getMessage(),
								e)
						)
				// Triggers reactive stream execution (non-blocking)
				.subscribe();
	}
}