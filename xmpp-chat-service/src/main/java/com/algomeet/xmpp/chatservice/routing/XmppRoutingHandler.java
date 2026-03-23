package com.algomeet.xmpp.chatservice.routing;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.session.XmppSessionManager;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import io.netty.channel.ChannelHandler; // Add this


@ChannelHandler.Sharable // Add this to allow one instance for all connections
@Component
public class XmppRoutingHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    // Reuse the factory (it's thread-safe)
    private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String xml = frame.text();
        
        try {
            // Parse the top-level attributes of the stanza (iq, message, or presence)
            Map<String, String> attributes = parseStanzaAttributes(xml);
            
            String to = attributes.get("to");
            String from = attributes.get("from");
            String id = attributes.get("id");
            String type = attributes.get("type");

            // 1. Session Management
            if (from != null) {
                XmppSessionManager.register(from, ctx.channel());
            }

            // 2. Routing Logic
            if (to != null && !to.equals("plays.shakespeare.lit")) {
                handleRouting(ctx, to, from, id, xml);
            } else {
                handleServerQuery(ctx, xml);
            }

        } catch (XMLStreamException e) {
            // Handle malformed XML (very common in XMPP hacking attempts)
            System.err.println("Malformed XML received: " + e.getMessage());
        }
    }

    private Map<String, String> parseStanzaAttributes(String xml) throws XMLStreamException {
        Map<String, String> attrMap = new HashMap<>();
        XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(new StringReader(xml));

        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    // We only care about the attributes of the root element (iq/message/presence)
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        attrMap.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                    }
                    break; // Exit after the first element
                }
            }
        } finally {
            reader.close();
        }
        return attrMap;
    }

    private void handleRouting(ChannelHandlerContext ctx, String to, String from, String id, String originalXml) {
        Channel targetChannel = XmppSessionManager.getChannel(to);
        
        if (targetChannel != null && targetChannel.isActive()) {
            targetChannel.writeAndFlush(new TextWebSocketFrame(originalXml));
        } else {
            String error = String.format(
                "<iq type='error' to='%s' id='%s'>" +
                "<error type='cancel'><service-unavailable xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/></error></iq>", 
                from, id);
            ctx.channel().writeAndFlush(new TextWebSocketFrame(error));
        }
    }

    private void handleServerQuery(ChannelHandlerContext ctx, String xml) {
        if (xml.contains("disco#info")) {
            String res = "<iq type='result' from='plays.shakespeare.lit' id='info1'>" +
                         "<query xmlns='http://jabber.org/protocol/disco#info'/></iq>";
            ctx.writeAndFlush(new TextWebSocketFrame(res));
        }
    }
}