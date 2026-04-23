package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.CallSession;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.repository.CallTrackerRepository;
import com.algomeet.xmpp.chatservice.util.JidUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Tracks 1-to-1 call lifecycle state in MongoDB.
 *
 * Responsibilities:
 * -------------------------------------------------------
 * 1. Store call initiation records.
 * 2. Mark call acceptance timestamps.
 * 3. Finalize calls and generate call logs.
 * 4. Handle abrupt disconnects.
 * 5. Coordinate clustered nodes safely with Redis locks.
 *
 * Typical Flow:
 * -------------------------------------------------------
 * INITIATE -> ACCEPT -> ACTIVE -> END
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallTrackerService {
	/**
	 * Repository abstraction for CallSession documents.
	 *
	 * Used for standard reactive CRUD operations.
	 */
	protected final CallTrackerRepository repository;
	
	/**
	 * Publishes messages to other cluster nodes.
	 *
	 * Allows call log messages to reach users connected
	 * on a different application server.
	 */
	private final ClusterMessagePublisher clusterMessagePublisher;

	/**
	 * Persists messages for offline delivery.
	 *
	 * If recipient is offline during call end,
	 * the call log can still be delivered later.
	 */
	private final OfflineMessageService offlineMessageService;

	/**
	 * Utility for converting full JIDs to bare JIDs.
	 *
	 * Example:
	 * user@domain/resource -> user@domain
	 */
	private final JidUtil jidUtil;

	/**
	 * Redis client used for distributed locking.
	 *
	 * Prevents duplicate finalization in clustered systems.
	 */
	private final RedissonReactiveClient redissonReactiveClient;
	
	private final UnreadCountService unreadCountService;
	

	/**
	 * Creates a new call session record when a call starts.
	 *
	 * Example:
	 * Caller taps audio/video call button.
	 *
	 * Stored fields:
	 * - call SID
	 * - caller JID
	 * - caller transport session
	 * - callee JID
	 * - call type
	 * - created timestamp
	 *
	 * @param sid unique call identifier
	 * @param caller caller JID
	 * @param callerSid caller connection/session id
	 * @param callee callee JID
	 * @param callType audio/video/etc
	 * @return saved CallSession
	 */
	public Mono<CallSession> trackInitiation(
			String sid,
			String caller,
			String callerSid,
			String callee,
			String callType) {

		CallSession call = CallSession.builder()
				.sid(sid)
				.caller(caller)
				.callerSid(callerSid)
				.callee(callee)
				.callType(callType)
				.createdAt(Instant.now().toEpochMilli())
				.build();

		return repository.save(call)
				.doOnSuccess(success ->
				log.info("Call session {} successfully save", sid))
				.doOnError(error ->
				log.error("Failed to save call initiated with SID: {}", sid, error));
	}

	/**
	 * Marks a call as accepted by the callee.
	 *
	 * Updates:
	 * - callee transport session id
	 * - acceptedAt timestamp
	 *
	 * This timestamp is later used to compute duration.
	 *
	 * @param sid call id
	 * @param callee callee jid
	 * @param calleeSid callee connection id
	 * @return updated CallSession
	 */
	public Mono<CallSession> trackAcceptance(
			String sid,
			String callee,
			String calleeSid) {

		return repository.findFirstBySidAndCalleeOrderByCreatedAtDesc(sid, callee)

				.flatMap(call -> {
					call.setCalleeSid(calleeSid);
					call.setAcceptedAt(Instant.now().toEpochMilli());
					return repository.save(call);
				})

				.doOnSuccess(success ->
				log.info("Call session {} successfully updated to accepted", sid))
				.doOnError(error ->
				log.error("Failed to mark Call SID {} as ACCEPTED", sid, error))
				.switchIfEmpty(
						Mono.error(new RuntimeException("Call not found for SID: " + sid)));
	}

	/**
	 * Deletes all records for a call SID.
	 *
	 * Usually used when session cleanup is required.
	 *
	 * @param sid call identifier
	 * @return completion signal
	 */
	public Mono<Void> remove(String sid) {
		return repository.deleteBySid(sid)
				.doOnSuccess(success ->
				log.info("Call session {} successfully deleted", sid))
				.doOnError(error ->
				log.error("Failed to delete Call SID {}", sid, error));
	}

	/**
	 * Finalizes a call exactly once.
	 *
	 * Why locking is needed:
	 * -------------------------------------------------------
	 * Both participants may disconnect nearly simultaneously,
	 * or multiple cluster nodes may process end-call events.
	 *
	 * Redis lock ensures only one worker finalizes the call.
	 *
	 * @param sid call identifier
	 * @param userSessionId requester connection id
	 * @param reason success / dropped / failed
	 * @return completion signal
	 */
	public Mono<Void> finalizeAndNotify(
			String sid,
			String userSessionId,
			String reason) {

		String lockKey = "algomeet:lock:finalize:call:" + sid;
		RLockReactive lock = redissonReactiveClient.getLock(lockKey);

		return Mono.usingWhen(
				/**
				 * Try acquiring lock:
				 * wait up to 500ms
				 * auto-expire after 2 seconds
				 */
				lock.tryLock(500, 2000, TimeUnit.MILLISECONDS),
				acquired -> {
					if (!acquired) {
						log.warn(
								"Lock acquisition failed for SID: {}. Skipping to prevent duplicates.",
								sid
								);
						return Mono.empty();
					}

					/**
					 * Lock acquired.
					 * Proceed with finalization.
					 */
					return executeFinalization(sid, userSessionId, reason);
				},

				/**
				 * Always unlock after completion.
				 */
				acquired -> acquired ? lock.unlock() : Mono.empty())
				.doOnError(e ->
				log.error("Critical failure in finalizeAndNotify for SID: {}", sid, e));
	}

	/**
	 * Internal finalization logic after lock is acquired.
	 *
	 * Steps:
	 * -------------------------------------------------------
	 * 1. Load call session.
	 * 2. Validate requester is participant.
	 * 3. Compute duration.
	 * 4. Send call logs to both users.
	 * 5. Delete call record.
	 */
	private Mono<Void> executeFinalization(
			String sid,
			String userSessionId,
			String reason) {

		return repository.findAllBySidAndRoomIdIsNull(sid)
				.switchIfEmpty(
						Mono.fromRunnable(() ->
						log.debug("SID {} already processed or not found.", sid)))
				.filter(session -> session.getRoomId() == null)
				.flatMap(session -> {

					/**
					 * Ignore malicious or stale requests
					 * from non-participants.
					 */
					if (!isParticipant(session, userSessionId)) {
						return Mono.empty();
					}

					long now = Instant.now().toEpochMilli();
					long duration = calculateDuration(session, now);
					String timestamp = formatIso(now);
					String status =
							"success".equalsIgnoreCase(reason)
							? "success"
									: "dropped";

					/**
					 * Queue call summary messages for both parties.
					 */
					sendCallLogs(session, sid, duration, status, timestamp);

					/**
					 * Delete tracker after messages are queued.
					 *
					 * Alternative production design:
					 * set processed=true instead of deleting.
					 */
					return repository.deleteBySid(sid);
				})
				.then();
	}

	/**
	 * Convert epoch millis into ISO-8601 UTC string.
	 *
	 * Example:
	 * 2026-04-10T13:24:22Z
	 */
	private String formatIso(long now) {
		return Instant.ofEpochMilli(now)
				.atOffset(ZoneOffset.UTC)
				.format(DateTimeFormatter.ISO_INSTANT);
	}

	/**
	 * Verifies whether a connection belongs to either
	 * participant of the call.
	 *
	 * Prevents unauthorized finalization attempts.
	 */
	private boolean isParticipant(
			CallSession session,
			String userSessionId) {
		return (
				userSessionId.equalsIgnoreCase(session.getCallerSid())
				|| userSessionId.equalsIgnoreCase(session.getCalleeSid()));
	}

	/**
	 * Calculates talk duration in seconds.
	 *
	 * If call was never accepted,
	 * duration remains zero.
	 */
	private long calculateDuration(
			CallSession session,
			long now) {

		if (session.getAcceptedAt() != null
				&& session.getAcceptedAt() > 0) {
			return (now - session.getAcceptedAt()) / 1000;
		}

		return 0;
	}

	/**
	 * Sends end-call log messages to caller and callee.
	 *
	 * Each side receives:
	 * - readable body text
	 * - structured <call-log/> extension
	 */
	private void sendCallLogs(
			CallSession session,
			String sid,
			long duration,
			String status,
			String ts) {

		String callerJid = jidUtil.getBareJid(session.getCaller());
		String calleeJid = jidUtil.getBareJid(session.getCallee());

		String callerMsgId = UUID.randomUUID().toString();
		String calleeMsgId = UUID.randomUUID().toString();

		// Send compose and send call logs to caller
		String callerMsg = composeCallLogStanza(
				callerMsgId,
				callerJid,
				calleeJid,
				sid,
				session.getCallType(),
				duration,
				status,
				ts);

		publish(callerMsgId, session.getCaller(), session.getCallee(), ChatType.CHAT, callerMsg);

		// Send compose and send call logs to responder/callee
		String calleeMsg = composeCallLogStanza(
				calleeMsgId,
				calleeJid,
				callerJid,
				sid,
				session.getCallType(),
				duration,
				status,
				ts);

		publish(calleeMsgId, session.getCallee(), session.getCaller(), ChatType.CHAT, calleeMsg);
	}

	/**
	 * Sends message through:
	 *
	 * 1. Offline storage
	 * 2. Cluster live routing
	 *
	 * This guarantees both persistence and live delivery.
	 */
	private void publish(
			String id,
			String to,
			String from,
			ChatType chatType,
			String payload) {

		offlineMessageService.save(id, to, from, XmppMessageType.CHAT.getXmlValue(), payload)
		.doOnSuccess(success -> {
			// Increment unread message counter
			unreadCountService.incrementUnreadCount(from, to);
		})
		.doOnError(e -> {
			log.error(
					"Storage failure for message {}: {}", id, e.getMessage(), e	);
			})
		.subscribe();

		/**
		 * Push immediately to connected nodes/users.
		 */
		clusterMessagePublisher.convertAndSendToUser(id, to, from, chatType, payload);
	}

	/**
	 * Builds XMPP call-log stanza.
	 *
	 * Example payload:
	 * <message>
	 *   <body>Video call ended...</body>
	 *   <call-log .../>
	 * </message>
	 */
	private String composeCallLogStanza(
			String id,
			String to,
			String from,
			String sid,
			String type,
			long duration,
			String status,
			String timestamp) {

		String stanza = String.format(
				"<message id='%s' to='%s' from='%s' type='chat'>" +
						"<body>%s call ended. Duration: %ds</body>" +
						"<call-log xmlns='https://algomeet.com/protocol/calls' " +
						"sid='%s' " +
						"type='%s' " +
						"duration='%d' " +
						"status='%s' " +
						"timestamp='%s' />" +
						"</message>",
						id, to, from,
						capitalize(type),
						duration,
						sid,
						type,
						duration,
						status,
						timestamp);

		log.debug("Outbound XMPP Stanza: {}", stanza);

		return stanza;
	}

	/**
	 * Uppercase first letter only.
	 *
	 * video -> Video
	 */
	private String capitalize(String str) {
		return (str == null || str.isEmpty())
				? "" : str.substring(0, 1).toUpperCase() + str.substring(1);
	}
		
	/**
	 * Delete by sid
	 * @param sid
	 * @return
	 */
	public Mono<Void> deleteBySid(String sid) {
		return repository.deleteBySid(sid);
	}
}