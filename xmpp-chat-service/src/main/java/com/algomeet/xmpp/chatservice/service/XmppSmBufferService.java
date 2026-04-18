package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.document.SmBufferMessage;
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
    public Mono<Void> saveStanza(String id, String receiverUserKey, String xml) {

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
                            && XmppStanzaUtil.isArchiveable(xml, xml))) {

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
    public Mono<SmBufferMessage> saveStanza(
            String id,
            String receiverUserKey,
            String xml,
            String smSessionId) {

        String seq = UlidCreator.getMonotonicUlid().toLowerCase();

        String type = XmppStanzaUtil.getAttribute(xml, "type");
        XmppMessageType msgType = XmppMessageType.fromString(type);

        // Apply same buffering rules as multi-session version
        if (!(msgType.supportsOfflineStorage()
                && XmppStanzaUtil.isArchiveable(xml, xml))) {

            return smBufferMessageService.bufferStanza(
                    smSessionId,

                    StringUtils.isEmpty(id)
                            ? UUID.randomUUID().toString()
                            : id,

                    seq,
                    xml);
        }

        return Mono.empty();
    }
}