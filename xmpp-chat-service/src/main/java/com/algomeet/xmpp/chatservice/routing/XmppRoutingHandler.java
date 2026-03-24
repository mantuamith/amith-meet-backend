package com.algomeet.xmpp.chatservice.routing;

import com.algomeet.xmpp.chatservice.auth.AuthAttributes;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.routing.handler.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.routing.handler.ServerQueryHandler;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.XmppSessionManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

@Slf4j
@ChannelHandler.Sharable
@Component
@AllArgsConstructor
public class XmppRoutingHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final ServerQueryHandler serverQueryHandler;
    private final UserSessionRegistry userSessionRegistry;
    private final ClusterMessagePublisher clusterMessagePublisher;
    
    private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String xml = frame.text();
        XmppPrincipal principal = ctx.channel().attr(AuthAttributes.PRINCIPAL).get();

        try {
            // 1. Handle Status Updates (Presence/Chat States)
            if (principal != null) {
                handleStatusUpdates(principal, xml);
            }

            // 2. Parse basic attributes for routing
            Map<String, String> attributes = parseStanzaAttributes(xml);
            String to = attributes.get("to");
            String from = attributes.get("from");
            String id = attributes.get("id");

            // 3. Routing Logic
            if (StringUtils.hasText(to)) {
            	handleRouting(ctx, id, to, from, xml);
            } else {
                serverQueryHandler.handleQuery(ctx, xml);
            }

        } catch (XMLStreamException e) {
            log.error("Malformed XML received: {}", e.getMessage());
        }
    }

    /**
     * Detects Presence (<show>) and Chat States (<active>/<inactive>)
     */
    /**
     * Expanded to handle GONE and DND statuses.
     */
    private void handleStatusUpdates(XmppPrincipal principal, String xml) {
        String userId = principal.getUserKey();
        String sessionId = principal.getSessionId();
        UserState newState = null;

        // 1. Handle Presence (<presence>)
        if (xml.contains("<presence")) {
            if (xml.contains("<show>dnd</show>")) {
                newState = UserState.DND;
            } else if (xml.contains("<show>away</show>") || xml.contains("<show>xa</show>")) {
                newState = UserState.AWAY;
            } else if (xml.contains("type='unavailable'")) {
                newState = UserState.GONE;
            } else {
                newState = UserState.ACTIVE;
            }
        } 
        
        // 2. Handle Chat States (<active>, <inactive>, <gone>)
        else if (xml.contains("<active")) {
            newState = UserState.ACTIVE;
        } 
        else if (xml.contains("<inactive")) {
            newState = UserState.INACTIVE;
        } 
        else if (xml.contains("<gone")) {
            newState = UserState.GONE;
        }

        // 3. Persist to Redis if a state change was detected
        if (newState != null) {
            userSessionRegistry.updateSessionStatus(userId, sessionId, newState);
            log.debug("Status for user {} updated to {}", principal.getUsername(), newState);
        }
    }

    private Map<String, String> parseStanzaAttributes(String xml) throws XMLStreamException {
        Map<String, String> attrMap = new HashMap<>();
        try (StringReader sr = new StringReader(xml)) {
            XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(sr);
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            attrMap.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                        }
                        break;
                    }
                }
            } finally {
                reader.close();
            }
        }
        return attrMap;
    }
    
    public void handleRouting(ChannelHandlerContext ctx, String id, String to, String from, String originalXml) {
    	// Sync message to cluster nodes
    	clusterMessagePublisher.convertAndSendToUser(id, XmppUtil.getUserKey(to), XmppUtil.getUserKey(from), originalXml);
    }   
}