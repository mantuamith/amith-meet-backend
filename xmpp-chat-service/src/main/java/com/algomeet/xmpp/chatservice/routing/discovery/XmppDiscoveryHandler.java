package com.algomeet.xmpp.chatservice.routing.discovery;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class XmppDiscoveryHandler {
    @Value("${xmpp.server.domain}")
    private String domain;

    private static final Pattern ID_PATTERN = Pattern.compile("id=['\"]([^'\"]+)['\"]");

    public void handleQuery(ChannelHandlerContext ctx, String xml) throws XMLStreamException {        
        // Only respond to Disco#Info requests
        if (xml.contains("http://jabber.org/protocol/disco#info")) {
            String requestId = extractId(xml);
            
            StringBuilder res = new StringBuilder();
            res.append(String.format("<iq type='result' from='%s' id='%s'>", domain, requestId));
            res.append("<query xmlns='http://jabber.org/protocol/disco#info'>");
            
            // 1. Identity
            res.append("<identity category='server' type='im' name='Algomeet Edge Server'/>");
            
            // 2. Core Features
            res.append("<feature var='http://jabber.org/protocol/disco#info'/>");
            res.append("<feature var='http://jabber.org/protocol/disco#items'/>");
            
            // 3. Modern Messaging Features (2026 Standards)
            res.append("<feature var='urn:xmpp:sm:3'/>");          // Stream Management
            //res.append("<feature var='urn:xmpp:mam:2'/>");         // Message Archive Management
            res.append("<feature var='urn:xmpp:receipts'/>");      // Delivery Receipts (XEP-0184)
            res.append("<feature var='urn:xmpp:chat-markers:0'/>");// Read Receipts (XEP-0333)
            res.append("<feature var='urn:xmpp:delay'/>");         // Delayed Delivery (XEP-0203)
            
            res.append("</query>");
            res.append("</iq>");

            ctx.writeAndFlush(new TextWebSocketFrame(res.toString()));
            log.debug("Sent Service Discovery response to client for ID: {}", requestId);
        }
    }

    /**
     * Extracts the 'id' attribute from the incoming XML so the response matches.
     */
    private String extractId(String xml) {
        Matcher matcher = ID_PATTERN.matcher(xml);
        return matcher.find() ? matcher.group(1) : "info_" + System.currentTimeMillis();
    }  
}