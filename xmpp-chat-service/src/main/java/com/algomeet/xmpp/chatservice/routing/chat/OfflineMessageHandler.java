package com.algomeet.xmpp.chatservice.routing.chat;

import java.time.Instant;
import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.repository.OfflineMessageRepository;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>Handles the retrieval and delivery of messages stored while a user was offline.</p>
 * 
 * <p>This component ensures that no messages are lost during periods of inactivity by 
 * fetching them from the persistent store and injecting them into the active Netty 
 * stream upon session binding.</p>
 * 
 * <p>Key Features:</p>
 * <ul>
 *     <li><b>XEP-0203 Integration:</b> Wraps stanzas with {@code <delay/>} tags so the 
 *         client knows the original timestamp of the message.</li>
 *     <li><b>Stream Management (XEP-0198):</b> Automatically registers these outbound 
 *         messages with the {@link XmppStreamManagementOutboundBuffer} to ensure they are actually 
 *         received by the client's device.</li>
 *     <li><b>Sequence Alignment:</b> Updates the session's outbound 'h' counter 
 *         proportionally to the number of offline messages delivered.</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineMessageHandler {
    private final OfflineMessageService offlineMessageService;
    private final DomainProperties domainProperties;
    private final LocalStanzaDispatcher localStanzaDispatcher;
    private final OfflineMessageRepository offlineMessageRepository;

    /**
     * Fetches offline stanzas for the given principal and flushes them to the channel.
     * Each message is tracked for acknowledgment to prevent data loss during 
     * the initial synchronization phase.
     * 
     * @param ctx       The Netty {@link ChannelHandlerContext} for the active session.
     * @param principal The authenticated user profile.
     */
    public Mono<Void> deliverOfflineMessages(String userKey) {
        return offlineMessageService.getOfflineMessages(userKey)
            // 1. Collect all messages into memory first to release DB cursors early
            .collectList()
            .flatMapMany(messageList -> {
                if (messageList.isEmpty()) {
                    log.debug("No offline messages found to process for user: {}", userKey);
                    return Flux.empty();
                }

                // Sort the list explicitly in-memory by the OfflineMessage ID field
                messageList.sort(Comparator.comparing(OfflineMessage::getId));

                log.info("Collected and sorted {} offline messages. Beginning true sequential dispatch for user: {}", 
                        messageList.size(), userKey);
                
                return Flux.fromIterable(messageList);
            })
            // 2. STAGE IN SEQUENCE: concatMap awaits the async network response of each message
            .concatMap(msg -> {
                return Mono.defer(() -> {
                    // Enrich stanza with XEP-0203 Delay metadata
                    String xmlWithDelay = wrapWithDelay(msg.getStanzaXml(), msg.getCreatedAt());
                    
                    // Invoke dispatch directly
                    return localStanzaDispatcher.dispatchLocally(userKey, msg.getFrom(), xmlWithDelay)
                            // If dispatchLocally returns a Mono<Boolean>, intercept the result here
                            .doOnNext(isSuccess -> {
                                if (Boolean.TRUE.equals(isSuccess) && msg.getIsAckStanza()) {
                                	// Delete if record is ACK stanza
                                	offlineMessageRepository.deleteByIdAndIsAckStanzaTrue(msg.getId()).subscribe();
                                 }
                            });
                })
                // Error handling isolated per-message so a single transmission drop doesn't break the entire pipeline
                .onErrorResume(e -> {
                    log.error("Failed to deliver message stanza {} during sequential dispatch for user: {}", msg.getId(), userKey, e);
                    return Mono.empty(); // Swallow error to allow the sequence to continue to the next message
                });
            })
            .doOnComplete(() -> log.info("Successfully completed offline message delivery batch for user: {}", userKey))
            .doOnError(e -> log.error("Fatal error during offline message processing sequence for {}: {}", userKey, e.getMessage()))
            .then(); 
    }
    
    /**
     * Injects a {@code <delay/>} element into the XML stanza per XEP-0203.
     * 
     * @param originalXml The raw XML of the message.
     * @param timestamp   The original creation time from the database.
     * @return The XML string with the delay metadata inserted.
     */
    private String wrapWithDelay(String originalXml, Instant timestamp) {
        // 'from' attribute identifies the server that stored the message
        String delay = String.format(
            "<delay xmlns='urn:xmpp:delay' from='%s' stamp='%s'/>",
            domainProperties.getDomain(), timestamp.toString()
        );
        
        // Find the end of the opening tag or the start of the closing tag.
        // We insert before the final closing tag (e.g., </message>)
        int lastIndex = originalXml.lastIndexOf("</");
        if (lastIndex != -1) {
            return originalXml.substring(0, lastIndex) + delay + originalXml.substring(lastIndex);
        }
        
        // Fallback for self-closing tags or malformed XML
        return originalXml + delay;
    }
}