package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppSmSessionRedisUtil;
import com.algomeet.xmpp.chatservice.util.XmppSmSessionsRedisUtil;
import com.algomeet.xmpp.chatservice.util.XmppSmUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handles Stream Management (XEP-0198) state tracking and stanza buffering.
 *
 * Responsibilities:
 * - Maintains SM session state in Redis
 * - Tracks active sessions per user
 * - Buffers stanzas for replay after reconnect/resume
 * - Applies offline/archival rules before persistence
 *
 * This service is critical for ensuring message reliability in
 * unstable network conditions and multi-device XMPP environments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XmppSmBufferService {
    private final XmppSmSessionRedisUtil xmppSmSessionRedisUtil;
    private final XmppSmSessionsRedisUtil xmppSmSessionsRedisUtil;
    private final SmBufferMessageService smBufferMessageService;
    private final RedissonReactiveClient redissonReactiveClient;

    /**
     * Initializes Stream Management session state for a connected client.
     *
     * This method is called when SM is enabled on a channel.
     *
     * Responsibilities:
     * - Stores inbound SM counter (h value)
     * - Registers session as active in Redis index
     * - Enables resume capability tracking
     *
     * @param ctx Netty channel context
     * @param principal authenticated XMPP user session
     */
    public Mono<Void> save(ChannelHandlerContext ctx, XmppPrincipal principal) {

        // Flag indicating whether Stream Management is enabled for this connection
        AtomicBoolean isEnabledSm =
                ctx.channel().attr(XmppSessionAttributes.SM_RESUMABLE_KEY).get();

        // If SM is not active, no session tracking is required
        if (isEnabledSm == null || !isEnabledSm.get()) {
            return Mono.empty();
        }

        // Unique SM session identifier bound to this connection
        String smSessionId =
                ctx.channel().attr(XmppSessionAttributes.SM_ID_KEY).get();

        // Persist SM session state and register it for resume tracking
        return xmppSmSessionRedisUtil
                .saveSessionState(smSessionId,
                        XmppSmUtil.getInboundH(ctx),
                        principal.getSessionId())

                // Index session under user for multi-device / resume lookup
                .doOnSuccess(success ->
                        xmppSmSessionsRedisUtil.addSessionToIndex(
                                principal.getUserKey(),
                                smSessionId)
                )
                .then();
    }

    /**
     * Retrieves all active SM session IDs for a given user.
     *
     * These sessions represent connected or resumable devices
     * that may require stanza buffering.
     */
    private Flux<String> getUserSmSessionIds(String receiverUserKey) {
        return xmppSmSessionsRedisUtil.getActiveNonExpiredSessions(receiverUserKey);
    }
    
    public Mono<Void> saveStanzaSynchronized(String id, String receiverUserKey, String xml) {    	
        String lockKey = "algomeet:lock:save:sm:id:" + id;
        RLockReactive lock = redissonReactiveClient.getLock(lockKey);

        return Mono.<Void, Boolean>usingWhen(
            // 1. ACQUIRE: Short wait, 2s lease
            lock.tryLock(500, 2000, TimeUnit.MILLISECONDS),
            
            acquired -> {
                if (!acquired) {
                    log.debug("Lock acquisition failed for stanza: {}. Potential duplicate or high contention.", id);
                    return Mono.empty();
                }
                // 2. PROCESS: Core logic
                return saveStanza(id, receiverUserKey, xml);
            },
            
            // 3. RELEASE: Success Cleanup
            acquired -> acquired ? safeUnlock(lock) : Mono.empty(),
            // 4. RELEASE: Error Cleanup
            (acquired, err) -> acquired ? safeUnlock(lock) : Mono.empty(),
            // 5. RELEASE: Cancel Cleanup
            acquired -> acquired ? safeUnlock(lock) : Mono.empty()
        )
        .doOnError(e -> log.error("Critical failure in saving for stanza ID: {}", id, e));
    }

    /**
     * Isolated unlock logic to handle Reactive Thread-Hopping.
     * Catches IllegalMonitorStateException to prevent operator 'onErrorDropped' crashes.
     */
    private Mono<Void> safeUnlock(RLockReactive lock) {
        return lock.unlock()
                .onErrorResume(IllegalMonitorStateException.class, e -> {
                    // This happens when the Netty thread changes between lock and unlock.
                    // We log at debug to keep logs clean, as the lock will expire anyway.
                    log.debug("Lock ownership lost or already released due to thread-hop: {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Buffers a stanza across all active SM sessions for a user.
     *
     * This ensures:
     * - reliable delivery after reconnect (XEP-0198)
     * - multi-device synchronization
     * - offline-safe message persistence (when applicable)
     *
     * Filtering rules:
     * - Only buffer messages that are NOT explicitly excluded
     *   by message type or archival policy
     */
    private Mono<Void> saveStanza(String id, String receiverUserKey, String xml) {
        // Monotonic sequence identifier for ordering replayed stanzas
        String seq = UlidCreator.getMonotonicUlid().toLowerCase();

        return getUserSmSessionIds(receiverUserKey)
                .flatMap(smSessionId -> {

                    // Extract message type from stanza attributes
                    String type = XmppStanzaUtil.getAttribute(xml, "type");
                    XmppMessageType msgType = XmppMessageType.fromString(type);

                    // Apply offline + archival rules:
                    // - Only store if message supports offline storage
                    // - AND stanza is not excluded from archiving (e.g., chatstate, transient events)
                    if (!(msgType.supportsOfflineStorage()
                            && XmppStanzaUtil.isArchiveable(xml))) {

                        return smBufferMessageService.bufferStanza(
                                smSessionId,

                                // Ensure ID fallback for stanzas without explicit id attribute
                                StringUtils.isEmpty(id)
                                        ? UUID.randomUUID().toString()
                                        : id,

                                seq,
                                xml);
                    }

                    // Skip buffering for transient or non-archivable stanzas
                    return Mono.empty();
                })
                .then()
                .doOnSuccess(v ->
                        log.debug("Completed processing stanza {} for user {}",
                                id, receiverUserKey)
                )
                .doOnError(e ->
                        log.error("Failed to process stanza {} for user {}",
                                id, receiverUserKey, e)
                );
    }

    /**
     * Buffers a stanza for a specific SM session.
     *
     * This is a targeted version used when session context is already known,
     * avoiding session lookup overhead.
     */   
    public Mono<Void> saveStanza(
    		String id,
            String receiverUserKey,
            String xml,
            String localSmSid) {
        // 1. Pre-calculate identifiers and logic once
        String seq = UlidCreator.getMonotonicUlid().toLowerCase();
        String stanzaId = StringUtils.isEmpty(id) ? UUID.randomUUID().toString() : id;
        
        String type = XmppStanzaUtil.getAttribute(xml, "type");
        XmppMessageType msgType = XmppMessageType.fromString(type);

        // 2. Determine eligibility once
        boolean shouldBuffer = msgType.supportsOfflineStorage() && XmppStanzaUtil.isArchiveable(xml);

        // Use the inverse logic as per your original snippet (!)
        if (!shouldBuffer) {
            return getUserSmSessionIds(receiverUserKey)
                    .collectList() // 3. Collect all session IDs first
                    .flatMap(smSessionIds -> {                 
                        if (StringUtils.isNotBlank(localSmSid) 
                        		&& !(smSessionIds.stream().anyMatch(s -> s.equalsIgnoreCase(localSmSid)))) {
                        	smSessionIds.add(localSmSid);
                        }
                        
                        // 4. Map the IDs to buffering tasks
                        return Flux.fromIterable(smSessionIds)
                                .flatMap(smSessionId -> 
                                    smBufferMessageService.bufferStanza(smSessionId, stanzaId, seq, xml)
                                )
                                .then();
                    })
                    .doOnSuccess(v -> log.debug("Completed processing stanza {} for user {}", stanzaId, receiverUserKey))
                    .doOnError(e -> log.error("Failed to process stanza {} for user {}", stanzaId, receiverUserKey, e));
        }

        // Skip buffering for transient/non-archivable stanzas
        return Mono.empty();
    }
}