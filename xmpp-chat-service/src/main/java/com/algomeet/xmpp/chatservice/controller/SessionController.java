package com.algomeet.xmpp.chatservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.controller.doc.SessionControllerDoc;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.SessionService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * REST controller for managing active XMPP and WebSocket sessions, as well as Redis-based presence states.
 * <p>
 * This controller provides endpoints for session maintenance and state cleanup. It is primarily used 
 * to synchronize the distributed state by removing stale or "zombie" records in Redis that 
 * track user presence (e.g., Active, Inactive).
 * </p>
 * <p>
 * <b>Security:</b> All operations are scoped to the authenticated user's context 
 * retrieved via {@link SecurityUtil}.
 * </p>
 *
 * @author Algomeet Core Team
 * @version 1.1
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class SessionController implements SessionControllerDoc {

	private final SessionService sessionService;

	/**
	 * Removes session state and clears user presence data from Redis.
	 * <p>
	 * This endpoint is utilized to prune "zombie" or orphan records from the distributed cache. 
	 * It performs the following:
	 * <ol>
	 * <li>Retrieves the unique {@code userKey} from the security context.</li>
	 * <li>Invokes {@link SessionService#removeSession} to purge presence metadata (Active/Inactive status) from Redis.</li>
	 * <li>Ensures the local node terminates any hanging Netty channels associated with the session.</li>
	 * </ol>
	 * </p>
	 *
	 * @param sessionId The unique identifier of the session/state to be evicted from Redis.
	 * @return A {@link Mono} wrapping the {@link ResponseEntity} containing a {@link CommonResponse} confirming the cleanup.
	 */
	@DeleteMapping("/{sessionId}")
	public Mono<ResponseEntity<CommonResponse<?>>> removeSession(
			@PathVariable String sessionId) {

		// Retrieve the identifier for the currently authenticated user
		String userKey = SecurityUtil.getUserKey();

		log.info("Request to evict presence state and remove orphan session {} for user {}", sessionId, userKey);

		// Execute the removal logic to clean up Redis state and local channels reactively
		return sessionService.removeSession(userKey, sessionId)
				.then(Mono.fromCallable(() -> {
					ResponseEntity<CommonResponse<?>> response = ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
					return response;
				}))
				.onErrorResume(e -> {
					log.error("Failed to clear Redis session/presence for user {} and session {}", userKey, sessionId, e);
					return Mono.error(e);
				});
	}
}