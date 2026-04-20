package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.document.CallSession;
import com.algomeet.xmpp.chatservice.enums.CallStatus;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.repository.CallTrackerRepository;
import com.algomeet.xmpp.chatservice.util.JidUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallTrackerService {

	private final CallTrackerRepository repository;
	private final ClusterMessagePublisher clusterMessagePublisher;
	private final OfflineMessageService offlineMessageService;    
	private final JidUtil jidUtil;
	private final RedissonReactiveClient redissonReactiveClient;
	private final ReactiveMongoTemplate mongoTemplate;

	/**
	 * Initiates the call record reactively.
	 */
	public Mono<CallSession> trackInitiation(String sid, String caller, String callerSid, String callee, String callType) {
		CallSession call = CallSession.builder()
				.sid(sid)
				.caller(caller)
				.callerSid(callerSid)
				.callee(callee)
				.callType(callType)
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
					return repository.save(call);
				})
				.doOnSuccess(success -> log.info("Call session {} successfully updated to accepted", sid))
				.doOnError(error -> log.error("Failed to mark Call SID {} as ACCEPTED", sid, error))
				.switchIfEmpty(Mono.error(new RuntimeException("Call not found for SID: " + sid)));
	}

	/**
	 * Remove the call record.
	 */
	public Mono<Void> remove(String sid) {
		return repository.deleteBySid(sid)
				.doOnSuccess(success -> log.info("Call session {} successfully deleted", sid))
				.doOnError(error -> log.error("Failed to delete Call SID {}", sid, error));
	}

	/**
	 * Finalizes session, notifies parties, and then handles the document lifecycle.
	 */
	public Mono<Void> finalizeAndNotify(String sid, String userSessionId, String reason) {
		String lockKey = "xmpp:save-lock:call:" + sid;
		RLockReactive lock = redissonReactiveClient.getLock(lockKey);

		return Mono.usingWhen(
				// 1. ACQUIRE: Wait 5ms, Lease 2s (prevents ghost locks)
				lock.tryLock(500, 2000, TimeUnit.MILLISECONDS),

				acquired -> {
					if (!acquired) {
						log.warn("Lock acquisition failed for SID: {}. Skipping to prevent duplicates.", sid);
						return Mono.empty();
					}

					// 2. PROCESS: The core logic now protected by the lock
					return executeFinalization(sid, userSessionId, reason);
				},

				// 3. RELEASE: Success/Cancel/Error - Always unlock
				acquired -> acquired ? lock.unlock() : Mono.empty()
				)
				.doOnError(e -> log.error("Critical failure in finalizeAndNotify for SID: {}", sid, e));
	}

	private Mono<Void> executeFinalization(String sid, String userSessionId, String reason) {
		return repository.findAllBySidAndRoomIdIsNull(sid)
				.switchIfEmpty(Mono.fromRunnable(() -> log.debug("SID {} already processed or not found.", sid)))
				.flatMap(session -> {
					// Validate participant
					if (!isParticipant(session, userSessionId)) return Mono.empty();

					// Prepare Data
					long now = Instant.now().toEpochMilli();
					long duration = calculateDuration(session, now);
					String timestamp = formatIso(now);
					String status = "success".equalsIgnoreCase(reason) ? "success" : "dropped";

					// Notification Logic
					sendCallLogs(session, sid, duration, status, timestamp);

					// 4. ATOMICITY: Delete after notifications are queued
					// In a true production environment, consider a 'processed' flag instead of deletion
					return repository.deleteBySid(sid);
				})
				.then();
	}

	// --- Helper Methods for Cleanliness ---    
	/**
	 * Formats a millisecond timestamp into a UTC ISO-8601 string.
	 * Example: 2026-04-10T13:24:22Z
	 */
	private String formatIso(long now) {
		return Instant.ofEpochMilli(now)
				.atOffset(ZoneOffset.UTC)
				.format(DateTimeFormatter.ISO_INSTANT);
	}

	private boolean isParticipant(CallSession session, String userSessionId) {
		return (userSessionId.equalsIgnoreCase(session.getCallerSid()) || 
				userSessionId.equalsIgnoreCase(session.getCalleeSid()));
	}

	private long calculateDuration(CallSession session, long now) {
		if (session.getAcceptedAt() != null && session.getAcceptedAt() > 0) {
			return (now - session.getAcceptedAt()) / 1000;
		}
		return 0;
	}

	private void sendCallLogs(CallSession session, String sid, long duration, String status, String ts) {
		String callerJid = jidUtil.getBareJid(session.getCaller());
		String calleeJid = jidUtil.getBareJid(session.getCallee());

		String callerMsgId = UUID.randomUUID().toString();
		String calleeMsgId = UUID.randomUUID().toString();

		String callerMsg = composeCallLogStanza(callerMsgId, callerJid, calleeJid, sid, session.getCallType(), duration, status, ts);
		publish(callerMsgId, session.getCaller(), session.getCallee(), ChatType.CHAT, callerMsg);

		String calleeMsg = composeCallLogStanza(calleeMsgId, calleeJid, callerJid, sid, session.getCallType(), duration, status, ts);
		publish(calleeMsgId, session.getCallee(), session.getCaller(), ChatType.CHAT, calleeMsg);
	}

	private void publish(String id, String to, String from, ChatType chatType, String payload) {    	
		offlineMessageService.save(id, to, from, XmppMessageType.CHAT.getXmlValue(), payload)
		.doOnError(e -> {
			log.error("Storage failure for message {}: {}", id, e.getMessage(), e);
		})
		.subscribe();

		// publish to cluster for synchronization
		clusterMessagePublisher.convertAndSendToUser(id, to, from, chatType, payload);
	}

	private String composeCallLogStanza(String id, String to, String from, String sid, String type, long duration, String status, String timestamp) {
		// Constructing the final XML payload
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
						id, to, from, capitalize(type), duration, sid, type, duration, status, timestamp
				);

		log.debug("Outbound XMPP Stanza: {}", stanza);

		return stanza;
	}

	private String capitalize(String str) {
		return (str == null || str.isEmpty()) ? "" : str.substring(0, 1).toUpperCase() + str.substring(1);
	}   
	
	/**
	 * Handles an abrupt transport layer disconnect (e.g., WebSocket closure).
	 * Instead of terminating the call for both parties, it marks only the 
	 * disconnected party as 'DROPPED', allowing for a potential reconnection.
	 *
	 * @param userSessionId The unique WebSocket/Connection ID that was lost.
	 * @return A Mono containing the updated CallSession or empty if no active call was found.
	 */
	public Mono<CallSession> handleTransportDrop(String userSessionId) {
	    // Look for any active session where this connection ID was either the caller or callee
	    return repository.findByCallerSidOrCalleeSid(userSessionId, userSessionId)
	        .next() // Take the most recent active session from the Flux
	        .filter(session -> session.getTerminatedAt() == null)
	        .flatMap(session -> {
	            // Determine asymmetrically which side of the call dropped
	            boolean isCaller = userSessionId.equals(session.getCallerSid());
	            String statusField = isCaller ? "callerStatus" : "calleeStatus";

	            // Use the primary key (_id) for the most performant atomic update
	            Query query = new Query(Criteria.where("id").is(session.getId()));
	            
	            // Atomically update only the status of the dropped party and set termination timestamp
	            Update update = new Update()
	                .set(statusField, CallStatus.DROPPED)
	                .set("terminatedAt", Instant.now().toEpochMilli());

	            log.info("Suspending session {} for SID: {}", userSessionId, session.getSid());

	            // findAndModify ensures we get the updated state back in one database round-trip
	            return mongoTemplate.findAndModify(
	                query,
	                update,
	                FindAndModifyOptions.options().returnNew(true),
	                CallSession.class
	            );
	        })
	        .doOnSuccess(s -> {
	            if (s != null) log.debug("Suspending call finished for session {}", s.getSid());
	        })
	        .doOnError(e -> log.error("Suspending call error for old session {}", userSessionId, e));
	}

	/**
	 * Re-attaches a new transport connection to an existing but 'DROPPED' call session.
	 * This effectively "stitches" the call back together after a user reconnects.
	 *
	 * @param oldUserSid The stale WebSocket/Connection ID that was previously dropped.
	 * @param newUserSid The new WebSocket/Connection ID generated upon reconnection.
	 * @return A Mono containing the restored CallSession.
	 */
	public Mono<CallSession> updateSessionRebind(String oldUserSid, String newUserSid) {
	    return repository.findByCallerSidOrCalleeSid(oldUserSid, oldUserSid)
	        .next() 
	        // Logic Gate: Only proceed if at least one party is currently in a DROPPED state
	        .filter(session -> session.getCallerStatus() == CallStatus.DROPPED || 
	                           session.getCalleeStatus() == CallStatus.DROPPED)
	        .flatMap(session -> {
	            // Identify which specific fields to "re-bind" to the new connection
	            boolean isCaller = oldUserSid.equals(session.getCallerSid());
	            String sidField = isCaller ? "callerSid" : "calleeSid";
	            String statusField = isCaller ? "callerStatus" : "calleeStatus";

	            // Query by ID to ensure we update the exact document found
	            Query query = new Query(Criteria.where("id").is(session.getId()));

	            // Restore the session: swap connection IDs, reset status, and clear termination timer
	            Update update = new Update()
	                .set(sidField, newUserSid)
	                .set(statusField, CallStatus.CONNECTED)
	                .set("terminatedAt", null);

	            log.info("Rebinding connection: {} -> {} for session: {}", oldUserSid, newUserSid, session.getSid());

	            return mongoTemplate.findAndModify(
	                query,
	                update,
	                FindAndModifyOptions.options().returnNew(true),
	                CallSession.class
	            );
	        })
	        .doOnSuccess(s -> {
	            if (s != null) log.debug("Rebind finished for session {}", s.getSid());
	        })
	        .doOnError(e -> log.error("Rebind error for old session {}", oldUserSid, e));
	}
}