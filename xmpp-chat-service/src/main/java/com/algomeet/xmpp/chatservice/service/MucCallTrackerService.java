package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLockReactive;
import org.redisson.api.RSemaphoreReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.CallSession;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.enums.CallStatus;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.CallTrackerRepository;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MucCallTrackerService {
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final XmppArchiveService xmppArchiveService;  
	private final JidUtil jidUtil;
	private final DomainProperties domainProperties;
	/**
	 * Redis distributed locking client.
	 *
	 * Used to avoid duplicate buffering when multiple nodes
	 * process the same stanza concurrently.
	 */
	private final RedissonReactiveClient redissonReactiveClient;

	/**
	 * Repository abstraction for CallSession documents.
	 *
	 * Used for standard reactive CRUD operations.
	 */
	protected final CallTrackerRepository repository;

	/**
	 * Initiates the call record reactively.
	 */
	public Mono<CallSession> trackInitiation(String sid, String caller, String callerSid, String callee, String callType, String roomId) {
		CallSession call = CallSession.builder()
				.sid(sid)
				.caller(caller)
				.callerSid(callerSid)
				.callee(callee)
				.callType(callType)
				.roomId(roomId)
				.status(CallStatus.INITIATED)
				.createdAt(Instant.now().toEpochMilli())
				.build();

		return repository.save(call)
				.doOnSuccess(success -> log.info("Call session {} successfully save", sid))
				.doOnError(error -> log.error("Failed to save call initiated with SID: {}", sid, error));
	}

	/**
	 * Updates acceptance status. 
	 * Uses flatMap to chain the lookup and the save.
	 */
	public Mono<CallSession> trackAcceptance(String sid, String callee, String calleeSid) {
		return repository.findFirstBySidAndCalleeOrderByCreatedAtDesc(sid, callee)
				.flatMap(call -> {
					call.setCalleeSid(calleeSid);
					call.setAcceptedAt(Instant.now().toEpochMilli());
					call.setStatus(CallStatus.ACTIVE);
					return repository.save(call);
				})
				.doOnSuccess(success -> log.info("MUC Call session {} successfully updated to accepted", sid))
				.doOnError(error -> log.error("Failed to mark MUC Call SID {} as ACCEPTED", sid, error))
				.switchIfEmpty(Mono.error(new RuntimeException("MUC Call not found for SID: " + sid)));
	}

	/**
	 * Retrieves all group/MUC call records for the given Session ID (SID).
	 *
	 * <p>
	 * Filters only records where {@code roomId} is present,
	 * meaning this lookup is intended for room/group call sessions
	 * rather than direct 1-to-1 calls.
	 * </p>
	 *
	 * @param sid unique Jingle / call session identifier
	 * @return stream of matching call sessions
	 */
	public Flux<CallSession> findBySid(String sid) {
		return repository.findAllBySidAndRoomIdIsNotNull(sid)
				.doOnSubscribe(sub ->
				log.debug("Fetching call sessions for SID: {}", sid)
						)
				.doOnComplete(() ->
				log.info("Successfully fetched call sessions for SID: {}", sid)
						)
				.doOnError(error ->
				log.error("Failed to fetch call sessions for SID: {}", sid, error)
						);
	}

	public Mono<CallSession> findFirstBySid(String sid) {
		return repository.findFirstBySidAndRoomIdIsNotNull(sid)
				.doOnSubscribe(sub ->
				log.debug("Fetching call sessions for SID: {}", sid)
						)
				.doOnError(error ->
				log.error("Failed to fetch call sessions for SID: {}", sid, error)
						);
	}

	/**
	 * Remove the call record.
	 */
	public Mono<Void> remove(String sid, String callee) {
		return repository.deleteBySidAndCallee(sid, callee)
				.doOnSuccess(success -> log.info("MUC Call session {} successfully deleted", sid))
				.doOnError(error -> log.error("Failed to delete MUC Call SID {}", sid, error));
	}

	/**
	 * Finalizes session, notifies parties, and then handles the document lifecycle.
	 */
	public Mono<Void> finalizeAndNotify(String sid, String userSessionId, String reason) {
		// A Semaphore with 1 permit acts exactly like a non-reentrant lock
	    RSemaphoreReactive semaphore = redissonReactiveClient.getSemaphore("xmpp:lock:finalize:sid:" + sid + ":user-session-id:" + userSessionId);

	    // tryAcquire(permits, waitTime, unit)
	    // permits: 1 (only one process can enter)
	    // waitTime: 0 (immediate fail-fast; if the permit is taken, we discard the redundant trigger)
	    return semaphore.tryAcquire(1, 0, TimeUnit.SECONDS) // Try to get 1 permit immediately
				.flatMap(acquired -> {
					if (!acquired) {
						// If lock is held, another process is already finalizing this SID.
						return Mono.empty();
					}

					return repository.findAllBySidAndRoomIdIsNotNull(sid)
							.collectList()
							.flatMap(sessions -> {
								if (sessions.isEmpty()) {
									return Mono.empty();
								}

								boolean isAuthorized = sessions.stream().anyMatch(session -> 
								userSessionId.equalsIgnoreCase(session.getCallerSid()));

								// Current UTC timestamp used for termination metadata.
								long now = Instant.now().toEpochMilli();

								return Flux.fromIterable(sessions)
										.filter(session -> session.getStatus() == CallStatus.ACTIVE)
										.flatMap(session ->
										finalizeAndNotifySynchronized(
												session.getId(),
												session.getSid(),
												session.getCallee(),
												reason
												))	
										.collectList()
										.doFinally(sig -> {	
											if (isAuthorized) {
												Optional<CallSession> callSession = sessions.stream()
														.filter(s -> s.getAcceptedAt() != null)
														.min(Comparator.comparingLong(CallSession::getAcceptedAt));

												// Compute connected call duration in seconds.
												// Duration starts only after the call was accepted.
												long duration = 0;
												if (callSession.get().getAcceptedAt() != null && callSession.get().getAcceptedAt() > 0) {
													duration = (now - callSession.get().getAcceptedAt()) / 1000;
												}

												// Caller is represented as room occupant JID.
												String roomJid =
														jidUtil.getGroupBareJid(callSession.get().getRoomId());

												// Caller receives call log message.
												String callerJid = jidUtil.getBareJid(callSession.get().getCaller());

												// Unique message ID for the generated call log stanza.
												String calleeMsgId = UUID.randomUUID().toString();

												// Normalize outbound call result status.
												String status = "success".equalsIgnoreCase(reason)
														? "success"	: "dropped";

												String isoTimestamp = Instant.ofEpochMilli(now)
														.atOffset(ZoneOffset.UTC)
														.format(DateTimeFormatter.ISO_INSTANT);

												// Build final call history / notification stanza.
												String calleeMsg = composeCallLogStanza(
														calleeMsgId,
														callerJid,
														roomJid,
														callSession.get().getSid(),
														callSession.get().getCallType(),
														duration,
														status,
														isoTimestamp);

												// Fire-and-forget publish of final call result to callee.
												publish(calleeMsgId,
														callSession.get().getCaller(),
														callSession.get().getCaller(),
														callSession.get().getRoomId(),
														ChatType.GROUPCHAT,
														calleeMsg);

												repository.deleteBySid(sid).subscribe();
											}

										})					
										.then();

							})
							.doOnSuccess(v ->
							log.info("MUC call session(s) for SID {} successfully finalized", sid))
							.doOnError(e ->
							log.error("Finalize failed for SID: {}", sid, e))
							// Use finalize to ensure unlock happens regardless of success/error/empty
							// Use doFinally to ensure the permit is ALWAYS released
	                        .doFinally(sig -> semaphore.release(1).subscribe()) 
	                        .then();
	            });
	}

	public Mono<Void> finalizeAndNotifySynchronized(String id,
			String sid, 
			String calleeUserKey,
			String reason) {

		/**
		 * Unique distributed lock key per call session record.
		 *
		 * If another server already holds it, this request
		 * likely represents duplicate processing.
		 */
		String lockKey = String.format("xmpp:lock:end:muc-call:sid:%s:callee:%s", sid, calleeUserKey);
		RLockReactive lock = redissonReactiveClient.getLock(lockKey);
		return Mono.<Void, Boolean>usingWhen(
				/**
				 * Step 1: Acquire lock.
				 *
				 * wait up to 300ms
				 * auto-expire after 20000ms
				 *
				 * Lease expiry prevents deadlocks if node crashes.
				 */
				lock.tryLock(300, 20000, TimeUnit.MILLISECONDS),

				acquired -> {
					if (!acquired) {

						/**
						 * Lock not obtained.
						 * Usually means another worker already handled it.
						 */
						log.debug("Lock acquisition failed for stanza: {}. Potential duplicate or high contention.",
								sid);

						return Mono.empty();
					}

					/**
					 * Lock acquired successfully.
					 * Proceed to core logic.
					 */
					return finalizeAndNotify(id, reason);
				},

				// Release on normal completion.
				acquired -> acquired ? safeUnlock(lock) : Mono.empty(),
						// Release on failure.
						(acquired, err) -> acquired ? safeUnlock(lock) : Mono.empty(),
								// Release on cancellation.
								acquired -> acquired ? safeUnlock(lock) : Mono.empty()
				)

				/**
				 * Log unexpected top-level failures.
				 */
				.doOnError(e -> {
					log.error("Critical failure in saving for stanza ID: {}", id, e);
				});
	}

	/**
	 * Finalizes an active call session and sends a call log notification
	 * to the callee.
	 *
	 * <p>This method only processes sessions that are not yet marked as
	 * {@link CallStatus#ENDED}. It records termination time, calculates
	 * call duration (if previously accepted), persists the updated state,
	 * and publishes a final call result stanza.</p>
	 *
	 * @param id     Unique call session record ID
	 * @param reason Call termination reason (e.g. success, dropped)
	 * @return completion signal when persistence is finished
	 */
	private Mono<Void> finalizeAndNotify(String id, String reason) {
		return repository.findById(id)

				// Ignore sessions already finalized.
				.filter(session -> session.getStatus() == CallStatus.ACTIVE)

				.flatMap(session -> {

					// Current UTC timestamp used for termination metadata.
					long now = Instant.now().toEpochMilli();

					String isoTimestamp = Instant.ofEpochMilli(now)
							.atOffset(ZoneOffset.UTC)
							.format(DateTimeFormatter.ISO_INSTANT);

					// Normalize outbound call result status.
					String status = "success".equalsIgnoreCase(reason)
							? "success"
									: "dropped";

					// Mark session as ended.
					session.setTerminatedAt(now);
					session.setStatus(CallStatus.ENDED);

					// Compute connected call duration in seconds.
					// Duration starts only after the call was accepted.
					long duration = 0;
					if (session.getAcceptedAt() != null && session.getAcceptedAt() > 0) {
						duration = (now - session.getAcceptedAt()) / 1000;
					}

					// Caller is represented as room occupant JID.
					String callerJid =
							jidUtil.getGroupBareJid(session.getRoomId()) + "/" + session.getCaller();

					// Callee receives a direct user JID message.
					String calleeJid = jidUtil.getBareJid(session.getCallee());

					// Unique message ID for the generated call log stanza.
					String calleeMsgId = UUID.randomUUID().toString();

					// Build final call history / notification stanza.
					String calleeMsg = composeCallLogStanza(
							calleeMsgId,
							calleeJid,
							callerJid,
							session.getSid(),
							session.getCallType(),
							duration,
							status,
							isoTimestamp);

					// Fire-and-forget publish of final call result to callee.
					publish(calleeMsgId,
							session.getCallee(),
							session.getCaller(),
							session.getRoomId(),
							ChatType.GROUPCHAT,
							calleeMsg);

					// Persist finalized session state.
					return repository.save(session).then();
				});
	}

	/**
	 * Unlock safely.
	 *
	 * Why needed:
	 * ----------------------------------------------------
	 * Reactive execution may switch threads internally.
	 * Some Redis lock implementations track owner thread.
	 *
	 * If unlock happens on a different thread,
	 * IllegalMonitorStateException may occur.
	 *
	 * We suppress it because:
	 * - lease expiration will free the lock
	 * - business flow should continue
	 */
	private Mono<Void> safeUnlock(RLockReactive lock) {
		return lock.unlock()
				.onErrorResume(IllegalMonitorStateException.class, e -> {
					log.debug(
							"Lock ownership lost or already released due to thread-hop: {}",
							e.getMessage()
							);
					return Mono.empty();
				})

				.then();
	}

	private void publish(String id, String to, String from, String toRoomId, ChatType chatType, String payload) {    
		StanzaInfo info = StanzaInfo.builder()
				.messageId(id)
				.type(payload)
				.build();

		String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();
		// Insert stanza ID
		String forArchiveXml = XmppStanzaUtil.insertStanzaId(payload, ulidString, domainProperties.getDomain());

		xmppArchiveService.archiveEvent(forArchiveXml, info, toRoomId, to, from, ulidString)
		.doFinally(signal -> {
			// publish to cluster for synchronization
			clusterMessagePublisher.convertAndSendToUser(id, to, from, chatType, forArchiveXml);
		})
		.doOnError(e -> {
			log.error("Storage failure for message {}: {}", id, e.getMessage(), e);
		})
		.subscribe();


	}

	private String composeCallLogStanza(String id, String to, String from, String sid, String type, long duration, String status, String timestamp) {
		// Constructing the final XML payload
		String stanza = String.format(
				"<message id='%s' to='%s' from='%s' type='groupchat'>" +
						"<body>%s call ended. Duration: %ds</body>" +
						"<call-log xmlns='https://algomeet.com/protocol/calls' " +
						"sid='%s' " +
						"type='%s' " +
						"duration='%d' " +
						"status='%s' " +
						"timestamp='%s' />" +
						"</message>",
						id, to, from, capitalize(type), duration, sid, type, duration, status, timestamp
				);

		log.debug("Outbound XMPP Stanza: {}", stanza);
		return stanza;
	}

	private String capitalize(String str) {
		return (str == null || str.isEmpty()) ? "" : str.substring(0, 1).toUpperCase() + str.substring(1);
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