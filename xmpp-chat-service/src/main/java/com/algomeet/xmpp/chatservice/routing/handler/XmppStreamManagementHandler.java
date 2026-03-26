package com.algomeet.xmpp.chatservice.routing.handler;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.XmppStreamAckTracker;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Handles XEP-0198 Stream Management protocol elements specifically for 
 * acknowledgments and synchronization.</p>
 * 
 * <p>This handler processes the two-way handshake of stream reliability:</p>
 * <ul>
 *     <li><b>Client Requests ({@code <r />}):</b> The client asks the server "How many 
 *         stanzas have you received?". This handler responds with an {@code <a h='...' />}.</li>
 *     <li><b>Client Acks ({@code <a h='...' />}):</b> The client tells the server "I have 
 *         received up to stanza X". This handler then reconciles the outbound buffer 
 *         and cleans up the persistent store (offline messages).</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 * @see <a href="https://xmpp.org/extensions/xep-0198.html">XEP-0198: Stream Management</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppStreamManagementHandler {

    private final XmppStreamAckTracker xmppStreamAckTracker;
    private final OfflineMessageService offlineMessageService; 
    
    /**
     * Processes incoming Stream Management XML elements.
     * 
     * @param ctx       The Netty {@link ChannelHandlerContext}.
     * @param xml       The raw XML string (either {@code <r/>} or {@code <a/>}).
     * @param principal The authenticated user session.
     */
    public void process(ChannelHandlerContext ctx, String xml, XmppPrincipal principal) {	
        if (isAckRequestFromClient(xml)) { 
            // The client is requesting an 'h' value from the server
            AtomicLong handledCount = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_KEY).get();
            
            if (handledCount != null) {
                ctx.writeAndFlush(new TextWebSocketFrame(new StreamAck(handledCount.get()).toXml()));
                log.trace("Responded to ack request from {} with h={}", principal.getUserKey(), handledCount.get());
            }
        } else {
            // The client is providing its 'h' value (how many stanzas it handled)
            long clientHandledCount = XmppUtil.parseHAttribute(xml);
            
            // 1. Identify which server-sent stanzas are now fully acknowledged by the client
            List<String> acknowledgedStanzaIds = xmppStreamAckTracker.acknowledgeUpTo(principal.getUserKey(), clientHandledCount);
            
            // 2. Clear acknowledged messages from the persistent 'offline' store
            if (!acknowledgedStanzaIds.isEmpty()) {
                for (String stanzaId : acknowledgedStanzaIds) {
                    offlineMessageService.deleteById(stanzaId).subscribe();
                }
                
                log.info("Purged {} acknowledged messages from store for {}, client handled count {}", acknowledgedStanzaIds, principal.getUserKey(), clientHandledCount);
            }
        }		
    }
    
    /**
     * Helper to determine if the incoming XML is a Stream Management control element.
     * 
     * @param xml Raw incoming XML string.
     * @return true if the element is an acknowledgment request or response.
     */
    public boolean isAckMessage(String xml) {
        return isAckRequestFromClient(xml) || isAckResponseFromClient(xml);	        
    }
    
    /**
     * Checks for the Ack Request tag: {@code <r xmlns='urn:xmpp:sm:3'/>}
     */
    private boolean isAckRequestFromClient(String xml) {
        // Includes basic string checks for performance; ideally uses namespace check if available
        return xml.contains("<r ") || xml.equals("<r/>") || xml.equals("<r />");
    }
    
    /**
     * Checks for the Ack Response tag: {@code <a h='...' xmlns='urn:xmpp:sm:3'/>}
     */
    private boolean isAckResponseFromClient(String xml) {
        return xml.startsWith("<a ") && xml.contains("h=");
    }
}