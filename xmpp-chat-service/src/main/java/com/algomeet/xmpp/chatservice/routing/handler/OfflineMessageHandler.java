package com.algomeet.xmpp.chatservice.routing.handler;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.document.OfflineMessage;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.XmppStreamAckTracker;

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
 *         messages with the {@link XmppStreamAckTracker} to ensure they are actually 
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
    private final XmppStreamAckTracker xmppStreamAckTracker;
    
    @Value("${xmpp.server.domain}")
    private String domain;

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
        List<OfflineMessage> messages = offlineMessageService.getOfflineMessages(userKey);
        
        if (messages.isEmpty()) {
            log.debug("No offline messages found for user: {}", userKey);
            return;
        }

        // Retrieve the session's outbound counter to maintain protocol sequence
        AtomicLong outboundH = ctx.channel().attr(XmppSessionAttributes.SM_OUTBOUND_H_KEY).get();
        
        if (outboundH == null) {
            log.error("Outbound SM counter not initialized for JID: {}. Cannot deliver safely.", userKey);
            return;
        }

        for (OfflineMessage msg : messages) {
            // Add metadata about when the message was originally sent
            String xmlWithDelay = wrapWithDelay(msg.getStanzaXml(), msg.getCreatedAt());
            
            // Push to WebSocket
            ctx.writeAndFlush(new TextWebSocketFrame(xmlWithDelay));
            
            // Track in the SM buffer so we can update DB status when the client acks
            xmppStreamAckTracker.track(userKey, outboundH.getAndIncrement(), msg.getId());
        }
        
        log.info("Delivered {} offline messages to user: {}", messages.size(), userKey);
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
            domain, timestamp.toString()
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