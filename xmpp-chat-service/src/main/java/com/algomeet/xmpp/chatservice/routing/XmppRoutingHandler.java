package com.algomeet.xmpp.chatservice.routing;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.service.NotificationService;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.routing.handler.JingleSessionOrchestrator;
import com.algomeet.xmpp.chatservice.routing.handler.XmppDirectChatHandler;
import com.algomeet.xmpp.chatservice.routing.handler.XmppInfoQueryHandler;
import com.algomeet.xmpp.chatservice.routing.handler.XmppMucHandler;
import com.algomeet.xmpp.chatservice.routing.handler.XmppSessionLifecycleHandler;
import com.algomeet.xmpp.chatservice.routing.handler.XmppStreamManagementHandler;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>The primary entry point for processing and routing all incoming XMPP stanzas 
 * over WebSocket frames.</p>
 * 
 * <p>This handler manages the high-level orchestration of a stanza's lifecycle:</p>
 * <ul>
 *     <li><b>Validation:</b> Ensures the incoming payload is well-formed XML.</li>
 *     <li><b>Lifecycle Management:</b> Updates presence and chat states (XEP-0085).</li>
 *     <li><b>Stream Management:</b> Handles {@code <r/>} and {@code <a/>} elements for 
 *         XEP-0198 reliability.</li>
 *     <li><b>Direct Routing:</b> Persists messages to {@link OfflineMessageService} and 
 *         broadcasts to the cluster via {@link ClusterMessagePublisher}.</li>
 *     <li><b>Error Handling:</b> Returns standardized XMPP error stanzas for parsing 
 *         or persistence failures.</li>
 * </ul>
 * 
 * <p>This handler is {@link ChannelHandler.Sharable @Sharable}, meaning a single instance 
 * is used across all Netty channels. Per-session state is retrieved via 
 * {@link XmppSessionAttributes}.</p>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@ChannelHandler.Sharable
@Component
@AllArgsConstructor
public class XmppRoutingHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
   
    private final XmppInfoQueryHandler xmppInfoQueryHandler;
    private final XmppSessionLifecycleHandler chatStateNotificationHandler;
    private final XmppStreamManagementHandler xmppStreamManagementHandler;
    private final XmppDirectChatHandler xmppDirectChatHandler;
    private final XmppMucHandler xmppMucHandler;

    /**
     * Entry point for incoming WebSocket text frames.
     * 
     * @param ctx   The channel context.
     * @param frame The WebSocket frame containing the XMPP XML string.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String xml = frame.text();
        XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();

        try {
            // 1. Lifecycle & Presence logic (XEP-0186 / XEP-0085)
            if (principal != null) {
                handleStatusUpdates(ctx, principal, xml);
            }

            // 2. Extract routing metadata without fully unmarshalling the whole stanza
            Map<String, String> attributes = XmppStanzaUtil.parseStanzaAttributes(xml);
            String to = attributes.get("to");
            String from = attributes.get("from");
            String id = attributes.get("id");
            String type = attributes.get("type");

            // 3. Routing Logic Branching
            if (StringUtils.hasText(to)) {
                // Stanza has a recipient (Message/IQ/Presence directed to others)
                handleRouting(ctx, id, XmppUtil.getUserKey(to), XmppUtil.getUserKey(from), type, xml);
            } else {
                // Stanza is a control element directed at the server (Stream Mgmt or Service Discovery)
                if (xmppStreamManagementHandler.isAckMessage(xml)) {
                    xmppStreamManagementHandler.process(ctx, xml, principal);
                } else {
                    xmppInfoQueryHandler.handleQuery(ctx, xml);
                }
            }

        } catch (XMLStreamException e) {
            log.error("Malformed XML received: {} , {}", xml, e.getMessage());
            XmppUtil.sendError(ctx, null, null, principal.getBareJid(), XmppErrorConditions.NOT_WELL_FORMED, "XML parsing failed");
        } catch (Exception e) {
             log.error("Routing error for XML {}: {}", xml, e.getMessage(), e);
        }
    }

    /**
     * Delegates status, presence, and chat state updates to the lifecycle handler.
     */   
    private void handleStatusUpdates(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
        chatStateNotificationHandler.handleStatusUpdates(ctx, principal, xml);
    }
       
    /**
     * Determines if the routing is for a 1-to-1 chat or a Multi-User Chat (Group).
     */
    public void handleRouting(ChannelHandlerContext ctx, String id, String to, String from, String type, String originalXml) {   	
        if ("groupchat".equalsIgnoreCase(type)) {
        	xmppMucHandler.handleGroupChatRouting(ctx, id, to, from, originalXml);
        } else {       	
        	xmppDirectChatHandler.handleDirectChatRouting(ctx, id, to, from, type, originalXml);
        }
    }    
}