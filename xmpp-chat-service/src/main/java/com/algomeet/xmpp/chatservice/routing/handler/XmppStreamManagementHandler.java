package com.algomeet.xmpp.chatservice.routing.handler;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
        if (isStreamManagementReq(xml)) { 
            // The client is requesting an 'h' value from the server
        	AtomicBoolean isEnabledSm = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY).get();
            AtomicLong handledCount = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_KEY).get();
            
            if (isEnabledSm != null && isEnabledSm.get() && handledCount != null) {
                ctx.writeAndFlush(new TextWebSocketFrame(new StreamAck(handledCount.get()).toXml()));
                log.trace("Responded to ack request from {} with h={}", principal.getUserKey(), handledCount.get());
            }
        } else if(isStreamManagementResp(xml)) {
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
        } else {
        	processSmEnable(ctx, xml);
        }
    }
    
    public void processSmEnable(ChannelHandlerContext ctx, String xml) {
        if (xml.contains("<enable")) {
            // 1. Extract the client's requested 'resume' preference (default to false if not found)
            boolean resumeRequested = xml.contains("resume='true'") || xml.contains("resume=\"true\"");
            
            // 2. Generate a unique SM ID if resumption is enabled
            String smId = resumeRequested ? UUID.randomUUID().toString() : null;

            // 3. Initialize Stream Management Counters (XEP-0198)
            // Using Atomic types for thread-safe increments during high-traffic routing           
            ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_KEY).set(new AtomicLong(0));
            ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY).set(new AtomicBoolean(true));
            
            // 4. Store resumption preference for session lifecycle management
            ctx.channel().attr(XmppSessionAttributes.SM_RESUMABLE_KEY).set(new AtomicBoolean(resumeRequested));
            if (smId != null) {
                ctx.channel().attr(XmppSessionAttributes.SM_ID_KEY).set(smId);
            }

            // 5. Build the <enabled /> response
            StringBuilder response = new StringBuilder("<enabled xmlns='urn:xmpp:sm:3'");
            if (smId != null) {
                response.append(String.format(" id='%s'", smId));
            }
            response.append(String.format(" resume='%b'/>", resumeRequested));

            // 6. Send the confirmation back to the client
            ctx.writeAndFlush(new TextWebSocketFrame(response.toString()));
            
            log.info("Stream Management enabled for session. Resumable: {}", resumeRequested);
        }
    }
    
    /**
     * Helper to determine if the incoming XML is a Stream Management control element.
     * 
     * @param xml Raw incoming XML string.
     * @return true if the element is an acknowledgment request or response.
     */
    public boolean isStreamManagementStanza(String xml) {
        return xml.contains("urn:xmpp:sm:3") || isStreamManagementReq(xml) || isStreamManagementResp(xml);	        
    }
    
    /**
     * Checks for the Ack Request tag: {@code <r xmlns='urn:xmpp:sm:3'/>}
     */
    private boolean isStreamManagementReq(String xml) {
        // Includes basic string checks for performance; ideally uses namespace check if available
        return xml.contains("<r ") || xml.equals("<r/>") || xml.equals("<r />");
    }
    
    /**
     * Checks for the Ack Response tag: {@code <a h='...' xmlns='urn:xmpp:sm:3'/>}
     */
    private boolean isStreamManagementResp(String xml) {
        return xml.contains("<a ") && xml.contains("h=");
    }
}