package com.algomeet.xmpp.chatservice.util;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.stanza.StanzaError;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class XmppUtil {
	private static final String DOMAIN_SEPARATOR = "@";
	private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();
	
	public static String getUserKey(String fullJid) {
		if(!StringUtils.hasText(fullJid)) {
			return null;
		}
		
		return fullJid.split(DOMAIN_SEPARATOR, 2)[0].trim();
	}
	
	public static String getRoomKey(String roomJid) {
		if(!StringUtils.hasText(roomJid)) {
			return null;
		}
		
		return roomJid.split(DOMAIN_SEPARATOR, 2)[0];
	}
	    
    /**
     * Simple regex or string manipulation to extract 'h' value
     */
    public static long parseHAttribute(String xml) {
        try {
            String hValue = xml.split("h='")[1].split("'")[0];
            return Long.parseLong(hValue);
        } catch (Exception e) {
        	log.error("Error parsing ack from client: {}", xml, e);
            return 0;
        }
    }
    
    /**
     * Extracts the text content of the <body> element from a raw XMPP XML string.
     * 
     * @param xml The raw XMPP message stanza.
     * @return The message body text, or null if no body is found.
     */
    public static String getMessageBody(String xml) {
        try (StringReader sr = new StringReader(xml)) {
            XMLStreamReader reader = XML_FACTORY.createXMLStreamReader(sr);
            while (reader.hasNext()) {
                int event = reader.next();
                
                // Look for the start of the <body> element
                if (event == XMLStreamConstants.START_ELEMENT && 
                    "body".equals(reader.getLocalName())) {
                    
                    // Return the text content immediately following the <body> tag
                    return reader.getElementText();
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract body from XML: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Utility to send standardized XMPP error frames back to the client.
     */
    public static void sendError(ChannelHandlerContext ctx, String id, String to, String from, String condition, String text) {
        StanzaError error = new StanzaError(id, to, from, condition, text);
        ctx.writeAndFlush(new TextWebSocketFrame(error.toXml()));
    }
}
