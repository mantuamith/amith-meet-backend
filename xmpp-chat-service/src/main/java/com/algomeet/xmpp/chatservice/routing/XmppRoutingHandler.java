package com.algomeet.xmpp.chatservice.routing;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.routing.handler.ChatStateNotificationHandler;
import com.algomeet.xmpp.chatservice.routing.handler.ServerQueryHandler;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.StanzaError;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
@Component
@AllArgsConstructor
public class XmppRoutingHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private final ServerQueryHandler serverQueryHandler;
    private final ClusterMessagePublisher clusterMessagePublisher;
    private final ChatStateNotificationHandler chatStateNotificationHandler;
    private final OfflineMessageService offlineMessageService;    
    private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String xml = frame.text();
        XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();

        try {
            // 1. Handle Status Updates (Presence/Chat States)
            if (principal != null) {
                handleStatusUpdates(ctx, principal, xml);
            }

            // 2. Parse basic attributes for routing
            Map<String, String> attributes = parseStanzaAttributes(xml);
            String to = attributes.get("to");
            String from = attributes.get("from");
            String id = attributes.get("id");
            String type = attributes.get("type");

            // 3. Routing Logic
            if (StringUtils.hasText(to)) {
            	handleRouting(ctx, id, to, from, type, xml);
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
    private void handleStatusUpdates(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
        chatStateNotificationHandler.handleStatusUpdates(ctx, principal, xml);;
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
    
    public void handleRouting(ChannelHandlerContext ctx, String id, String to, String from, String type, String originalXml) {    	
    	// Get or Initialize the per-session counter
        AtomicLong handledCount = ctx.channel().attr(XmppSessionAttributes.HANDLED_COUNT_KEY).get();
        
        if (XmppMessageType.fromString(type).supportsOfflineStorage()) {  
            offlineMessageService.save(id, to, from, type, originalXml)
                .doOnSuccess(savedDoc -> {
                    if (handledCount != null) {
                        ctx.writeAndFlush(new TextWebSocketFrame(new StreamAck(handledCount.incrementAndGet()).toXml()));
                    }
                })
                .doOnError(e -> {
                    // The error 'to' is the original 'from'
                    StanzaError error = new StanzaError(
                        id, 
                        from, // Recipient of error is the original sender
                        to,   // Sender of error is the intended recipient's domain/server
                        XmppErrorConditions.INTERNAL_SERVER_ERROR,
                        "Database persistence failed"
                    );
                    ctx.writeAndFlush(new TextWebSocketFrame(error.toXml()));
                })
                .subscribe();
        }
    	    	
        if ("groupchat".equalsIgnoreCase(type)) {
            // Handle Multi-User Chat (MUC) Fan-out
            handleGroupChatRouting(id, to, from, originalXml);
        } else {       	
            // Default to Direct Chat (One-to-One)
            handleDirectChatRouting(id, to, from, originalXml);
        }
    }

    private void handleDirectChatRouting(String id, String to, String from, String originalXml) {
        // Sync to nodes where the specific user is connected
        clusterMessagePublisher.convertAndSendToUser(id, XmppUtil.getUserKey(to), XmppUtil.getUserKey(from), originalXml);
    }

    private void handleGroupChatRouting(String id, String roomJid, String from, String originalXml) {
        // Sync to the 'Room' topic so all nodes with participants in this room receive it
        //clusterMessagePublisher.convertAndSendToRoom(id, XmppUtil.getRoomKey(roomJid), XmppUtil.getUserKey(from), originalXml);
    }    
    
}