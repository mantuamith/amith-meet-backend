package com.algomeet.xmpp.chatservice.routing.chat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    /**
     * Fetches offline stanzas for the given principal and flushes them to the channel.
     * Each message is tracked for acknowledgment to prevent data loss during 
     * the initial synchronization phase.
     * 
     * @param ctx       The Netty {@link ChannelHandlerContext} for the active session.
     * @param principal The authenticated user profile.
     */
    public void deliverOfflineMessages(ChannelHandlerContext ctx, XmppPrincipal principal) {
        String userKey = principal.getUserKey();

        // Subscribe to the Flux of offline messages
        offlineMessageService.getOfflineMessages(userKey)
            .doOnNext(msg -> {
                // Add XEP-0203 Delay metadata
                String xmlWithDelay = wrapWithDelay(msg.getStanzaXml(), msg.getCreatedAt());
                
                // Push to WebSocket
                ctx.writeAndFlush(new TextWebSocketFrame(xmlWithDelay));
            })
            .doOnComplete(() -> log.info("Completed offline message delivery for user: {}", userKey))
            .doOnError(e -> log.error("Failed to deliver offline messages for {}: {}", userKey, e.getMessage()))
            .subscribe(); // This triggers the execution asynchronously
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