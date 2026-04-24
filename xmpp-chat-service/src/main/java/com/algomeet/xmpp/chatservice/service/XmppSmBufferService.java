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
 * Stream Management (XEP-0198) buffer coordinator.
 *
 * Primary goals:
 * ----------------------------------------------------
 * 1. Preserve delivery guarantees during disconnects.
 * 2. Support session resume after reconnect.
 * 3. Synchronize message delivery across devices.
 * 4. Persist resumable session state in Redis.
 *
 * Why this service matters:
 * ----------------------------------------------------
 * Mobile clients frequently disconnect due to:
 * - network switching (WiFi <-> mobile data)
 * - background app suspension
 * - poor signal
 * - temporary packet loss
 *
 * Instead of losing messages, Stream Management allows
 * the server to resume the previous session and replay
 * any unacknowledged stanzas from the buffer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XmppSmBufferService {

    /**
     * Stores individual SM session metadata:
     * - inbound acknowledged counter (h)
     * - owning app session
     * - expiration / resumable state
     */
    private final XmppSmSessionRedisUtil xmppSmSessionRedisUtil;

    /**
     * Stores user -> session index.
     *
     * Example:
     * user A may have:
     * - phone session
     * - tablet session
     * - web session
     */
    private final XmppSmSessionsRedisUtil xmppSmSessionsRedisUtil;

    /**
     * Persists buffered stanzas per SM session.
     *
     * Used when replaying after reconnect/resume.
     */
    private final SmBufferMessageService smBufferMessageService;

    /**
     * Redis distributed locking client.
     *
     * Used to avoid duplicate buffering when multiple nodes
     * process the same stanza concurrently.
     */
    private final RedissonReactiveClient redissonReactiveClient;

    /**
     * Save Stream Management session state when SM becomes enabled.
     *
     * Typical flow:
     * ----------------------------------------------------
     * Client sends:
     *   <enable xmlns='urn:xmpp:sm:3'/>
     *
     * Server responds and creates:
     * - session id
     * - resume metadata
     * - counters
     *
     * This method persists that state in Redis so another node
     * can restore it later during resume.
     *
     * @param ctx       current Netty channel
     * @param principal authenticated user session
     * @return completion signal
     */
    public Mono<Void> save(ChannelHandlerContext ctx, XmppPrincipal principal) {

        /**
         * Channel attribute that indicates whether the client
         * successfully enabled Stream Management.
         *
         * AtomicBoolean is used because channel attributes may be
         * mutated safely by concurrent event-loop operations.
         */
        AtomicBoolean isEnabledSm =
                ctx.channel().attr(XmppSessionAttributes.SM_RESUMABLE_KEY).get();

        /**
         * If Stream Management is not enabled:
         * - nothing to persist
         * - no resume support
         * - no counters required
         */
        if (isEnabledSm == null || !isEnabledSm.get()) {
            return Mono.empty();
        }

        /**
         * Unique Stream Management Session ID.
         *
         * Sent to client during <enabled id='xyz' resume='true'/>
         */
        String smSessionId =
                ctx.channel().attr(XmppSessionAttributes.SM_ID_KEY).get();

        return xmppSmSessionRedisUtil

                /**
                 * Save per-session metadata:
                 * - sm session id
                 * - latest inbound h counter
                 * - logical application session id
                 */
                .saveSessionState(
                        smSessionId,
                        XmppSmUtil.getInboundH(ctx),
                        principal.getSessionId()
                )

                /**
                 * Add this SM session into the user's active session index.
                 *
                 * Needed for:
                 * - multi-device fanout
                 * - cleanup
                 * - resume lookup
                 */
                .doOnSuccess(success ->
                        xmppSmSessionsRedisUtil.addSessionToIndex(
                                principal.getUserKey(),
                                smSessionId)
                )

                .then();
    }

    /**
     * Returns all active + non-expired Stream Management sessions
     * owned by a user.
     *
     * Example:
     * user123 may return:
     * - sm-phone-1
     * - sm-web-2
     * - sm-tablet-3
     *
     * @param receiverUserKey target user id
     * @return stream of SM session ids
     */
    private Flux<String> getUserSmSessionIds(String receiverUserKey) {
        return xmppSmSessionsRedisUtil.getActiveNonExpiredSessions(receiverUserKey);
    }

    /**
     * Save stanza with distributed synchronization.
     *
     * Why lock is needed:
     * ----------------------------------------------------
     * In clustered deployments, the same stanza may arrive
     * through retries, duplicate routing, or parallel workers.
     *
     * Locking by stanza id ensures only one node processes it.
     *
     * @param id stanza id
     * @param receiverUserKey recipient user
     * @param xml raw stanza xml
     * @return completion signal
     */
    public Mono<Void> saveStanzaSynchronized(
            String id,
            String receiverUserKey,
            String xml) {

        /**
         * Unique distributed lock key per stanza id.
         *
         * If another server already holds it, this request
         * likely represents duplicate processing.
         */
        String lockKey = "xmpp:lock:save:sm:stanza-id:" + id;

        RLockReactive lock = redissonReactiveClient.getLock(lockKey);

        return Mono.<Void, Boolean>usingWhen(

            /**
             * Step 1: Acquire lock.
             *
             * wait up to 500ms
             * auto-expire after 2000ms
             *
             * Lease expiry prevents deadlocks if node crashes.
             */
            lock.tryLock(500, 2000, TimeUnit.MILLISECONDS),

            acquired -> {
                if (!acquired) {

                    /**
                     * Lock not obtained.
                     * Usually means another worker already handled it.
                     */
                    log.debug(
                        "Lock acquisition failed for stanza: {}. Potential duplicate or high contention.",
                        id
                    );

                    return Mono.empty();
                }

                /**
                 * Lock acquired successfully.
                 * Proceed to core buffering logic.
                 */
                return saveStanza(id, receiverUserKey, xml);
            },

            /**
             * Release on normal completion.
             */
            acquired -> acquired ? safeUnlock(lock) : Mono.empty(),

            /**
             * Release on failure.
             */
            (acquired, err) -> acquired ? safeUnlock(lock) : Mono.empty(),

            /**
             * Release on cancellation.
             */
            acquired -> acquired ? safeUnlock(lock) : Mono.empty()
        )

        /**
         * Log unexpected top-level failures.
         */
        .doOnError(e ->
                log.error("Critical failure in saving for stanza ID: {}", id, e)
        );
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

    /**
     * Buffer stanza to ALL active SM sessions of recipient.
     *
     * This is used when:
     * - recipient has multiple devices
     * - user disconnected temporarily
     * - message must replay after resume
     *
     * @param id stanza id
     * @param receiverUserKey recipient user
     * @param xml raw stanza
     * @return completion signal
     */
    private Mono<Void> saveStanza(
            String id,
            String receiverUserKey,
            String xml) {

        /**
         * Ordered monotonic ULID.
         *
         * Benefits:
         * - sortable by creation time
         * - globally unique
         * - preserves replay ordering
         */
        String seq = UlidCreator.getMonotonicUlid().toLowerCase();

        return getUserSmSessionIds(receiverUserKey)

                .flatMap(smSessionId -> {
                    /**
                     * Read message type from stanza.
                     *
                     * Examples:
                     * chat, groupchat, normal, headline
                     */
                    String type = XmppStanzaUtil.getAttribute(xml, "type");
                    XmppMessageType msgType =
                            XmppMessageType.fromString(type);

                    /**
                     * Original logic preserved:
                     *
                     * If stanza is NOT eligible for normal offline/archive
                     * handling, buffer into SM replay queue.
                     */
                    if (!(msgType.supportsOfflineStorage()
                            && XmppStanzaUtil.isArchivable(xml))) {

                        return smBufferMessageService.bufferStanza(
                                smSessionId,
                                
                                /**
                                 * Generate fallback id if sender omitted stanza id.
                                 */
                                StringUtils.isEmpty(id)
                                        ? UUID.randomUUID().toString()
                                        : id,

                                seq,
                                xml
                        );
                    }
                    /**
                     * Skip stanza from SM buffer path.
                     */
                    return Mono.empty();
                })
                .then()
                .doOnSuccess(v ->
                        log.debug(
                                "Completed processing stanza {} for user {}",
                                id,
                                receiverUserKey
                        )
                )
                .doOnError(e ->
                        log.error(
                                "Failed to process stanza {} for user {}",
                                id,
                                receiverUserKey,
                                e
                        )
                );
    }

    /**
     * Targeted buffering version when caller already knows
     * a specific local SM session id.
     *
     * This avoids missing the currently connected session
     * if Redis index is slightly delayed.
     *
     * @param id stanza id
     * @param receiverUserKey recipient user
     * @param xml raw stanza
     * @param localSmSid locally known session id
     * @return completion signal
     */
    public Mono<Void> saveStanza(
            String id,
            String receiverUserKey,
            String xml,
            String localSmSid) {

        /**
         * Generate ordered replay sequence.
         */
        String seq = UlidCreator.getMonotonicUlid().toLowerCase();

        /**
         * Guarantee stanza id exists.
         */
        String stanzaId =
                StringUtils.isEmpty(id)
                        ? UUID.randomUUID().toString()
                        : id;

        /**
         * Determine stanza semantics once.
         */
        String type = XmppStanzaUtil.getAttribute(xml, "type");

        XmppMessageType msgType =
                XmppMessageType.fromString(type);

        /**
         * Eligibility rule for normal storage/archive path.
         */
        boolean shouldBuffer =
                msgType.supportsOfflineStorage()
                        && XmppStanzaUtil.isArchivable(xml);

        /**
         * Preserve existing inverse logic:
         * Only SM-buffer when shouldBuffer == false
         */
        if (!shouldBuffer) {

            return getUserSmSessionIds(receiverUserKey)

                    /**
                     * Gather sessions first so we can merge local sid.
                     */
                    .collectList()

                    .flatMap(smSessionIds -> {

                        /**
                         * Add local session if not present in Redis index.
                         *
                         * Useful during race conditions immediately after login.
                         */
                        if (StringUtils.isNotBlank(localSmSid)
                                && !(smSessionIds.stream()
                                .anyMatch(s ->
                                        s.equalsIgnoreCase(localSmSid)))) {

                            smSessionIds.add(localSmSid);
                        }

                        /**
                         * Buffer same stanza into each session queue.
                         */
                        return Flux.fromIterable(smSessionIds)

                                .flatMap(smSessionId ->
                                        smBufferMessageService.bufferStanza(
                                                smSessionId,
                                                stanzaId,
                                                seq,
                                                xml
                                        )
                                )

                                .then();
                    })
                    .doOnSuccess(v ->
                            log.debug(
                                    "Completed processing stanza {} for user {}",
                                    stanzaId,
                                    receiverUserKey
                            )
                    )
                    .doOnError(e ->
                            log.error(
                                    "Failed to process stanza {} for user {}",
                                    stanzaId,
                                    receiverUserKey,
                                    e
                            )
                    );
        }

        /**
         * Skip transient / archive-managed stanzas.
         */
        return Mono.empty();
    }
}