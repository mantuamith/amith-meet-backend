package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.CallSession;
import com.algomeet.xmpp.chatservice.enums.ParticipantCallStatus;
import com.algomeet.xmpp.chatservice.repository.CallTrackerRepository;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * CallSessionRecoveryService handles recovery of call sessions in failure scenarios.
 *
 * <p>
 * This service is responsible for maintaining continuity of active calls
 * when transport-level disruptions occur (e.g., websocket disconnects,
 * network failures, or application crashes).
 * </p>
 *
 * <h3>Core Responsibilities</h3>
 * <ul>
 *   <li>Detect and mark participant disconnect events</li>
 *   <li>Allow temporary suspension of active calls (not immediate termination)</li>
 *   <li>Restore sessions when users reconnect with a new transport SID</li>
 *   <li>Maintain consistent session state across distributed nodes</li>
 * </ul>
 *
 * <h3>State Transition Flow</h3>
 * <pre>
 * ACTIVE → DISCONNECTED → RESUMED → ACTIVE
 * </pre>
 *
 * <p>
 * This class ensures that transient network issues do not cause
 * premature call termination.
 * </p>
 */
@Slf4j
@Data
@Service
@RequiredArgsConstructor
public class CallSessionRecoveryService {

	/**
	 * Reactive repository for CallSession persistence.
	 *
	 * Used for high-level query operations such as finding sessions
	 * by caller or callee SID.
	 */
	private final CallTrackerRepository repository;

	/**
	 * Reactive MongoDB template for atomic updates.
	 *
	 * Used for safe concurrent updates where multiple nodes
	 * may attempt to modify the same call session simultaneously.
	 *
	 * Typical operations:
	 * - findAndModify
	 * - partial field updates
	 * - state transitions
	 */
	private final ReactiveMongoTemplate mongoTemplate;

	/**
	 * Handles transport-level disconnect events for a participant.
	 *
	 * <p>
	 * This method does NOT terminate the call.
	 * Instead, it marks only the affected participant as DISCONNECTED
	 * and sets a temporary termination timestamp.
	 * </p>
	 *
	 * <p>
	 * This allows:
	 * <ul>
	 *   <li>Graceful reconnection within a short time window</li>
	 *   <li>Preservation of the other participant's session state</li>
	 *   <li>Deferred finalization logic in higher layers</li>
	 * </ul>
	 * </p>
	 *
	 * @param userSessionId transport/session identifier that lost connection
	 * @return updated CallSession reflecting disconnected state
	 */
	public Mono<CallSession> handleTransportDrop(String userSessionId) {

		return repository.findByCallerSidOrCalleeSid(
				userSessionId,
				userSessionId
		)
		// Only process the latest active session
		.next()

		// Ignore already terminated sessions
		.filter(session -> session.getTerminatedAt() == null)

		.flatMap(session -> {

			/**
			 * Determine which participant disconnected.
			 * This controls which field is updated.
			 */
			boolean isCaller = userSessionId.equals(session.getCallerSid());
			String statusField = isCaller ? "callerStatus" : "calleeStatus";
			Query query = new Query(Criteria.where("id").is(session.getId()));

			/**
			 * Mark only one side as DISCONNECTED.
			 * Do NOT end the call globally.
			 */
			Update update = new Update()
					.set(statusField, ParticipantCallStatus.DISCONNECTED)
					.set("terminatedAt", Instant.now().toEpochMilli());

			return mongoTemplate.findAndModify(
					query,
					update,
					FindAndModifyOptions.options().returnNew(true),
					CallSession.class
			);
		})

		.doOnSuccess(session -> {
			if (session != null) {
				log.info("Call session suspended due to transport drop: {}", session.getSid());
			}
		})

		.doOnError(e ->
			log.error("Failed to handle transport drop for session {}", userSessionId, e)
		);
	}

	/**
	 * Rebinds a call session when a participant reconnects with a new transport SID.
	 *
	 * <p>
	 * This is used when:
	 * <ul>
	 *   <li>User reconnects after network drop</li>
	 *   <li>WebSocket session is recreated</li>
	 *   <li>Client re-establishes XMPP session</li>
	 * </ul>
	 * </p>
	 *
	 * <p>
	 * The method updates:
	 * <ul>
	 *   <li>Old SID → New SID mapping</li>
	 *   <li>Participant status → RESUMED</li>
	 *   <li>Clears temporary termination state</li>
	 * </ul>
	 * </p>
	 *
	 * @param oldUserSid previous transport/session identifier
	 * @param newUserSid newly assigned transport/session identifier
	 * @return stream of restored CallSession records
	 */
	public Flux<CallSession> updateSessionRebind(
			String oldUserSid,
			String newUserSid) {

		return repository.findByCallerSidOrCalleeSid(
				oldUserSid,
				oldUserSid
		)

		// Only attempt rebind if participant was previously disconnected
		.filter(session -> {
			log.debug(
				"Evaluating rebind: session={}, callerSid={}, callerStatus={}, calleeSid={}, calleeStatus={}",
				session.getSid(),
				session.getCallerSid(),
				session.getCallerStatus(),
				session.getCalleeSid(),
				session.getCalleeStatus()
			);

			return session.getCallerStatus() == ParticipantCallStatus.DISCONNECTED
				|| session.getCalleeStatus() == ParticipantCallStatus.DISCONNECTED;
		})

		.flatMap(session -> {

			/**
			 * Identify which participant is re-binding.
			 */
			boolean isCaller = oldUserSid.equals(session.getCallerSid());

			String sidField = isCaller ? "callerSid" : "calleeSid";
			String statusField = isCaller ? "callerStatus" : "calleeStatus";
			Query query = new Query(Criteria.where("id").is(session.getId()));

			/**
			 * Update SID and restore active state.
			 */
			Update update = new Update()
					.set(sidField, newUserSid)
					.set(statusField, ParticipantCallStatus.RESUMED)
					.set("terminatedAt", null)
					.set("updatedAt", Instant.now());

			log.info(
				"Rebinding session [{}] role={} oldSid={} newSid={}",
				session.getSid(),
				isCaller ? "CALLER" : "CALLEE",
				oldUserSid,
				newUserSid
			);

			return mongoTemplate.findAndModify(
					query,
					update,
					FindAndModifyOptions.options().returnNew(true),
					CallSession.class
			);
		})

		.doOnNext(session ->
			log.debug("Rebind completed for session {}", session.getSid())
		)

		.doOnError(e ->
			log.error("Failed to rebind session for oldSid={}", oldUserSid, e)
		);
	}
}