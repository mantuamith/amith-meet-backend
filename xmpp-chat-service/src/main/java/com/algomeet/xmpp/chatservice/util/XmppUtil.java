package com.algomeet.xmpp.chatservice.util;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.stanza.StanzaError;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

/**
 * General utility class for XMPP protocol manipulation, JID parsing, and stanza handling.
 * <p>
 * This class provides lightweight methods for common XMPP tasks, including extracting 
 * identifiers from JIDs and performing fast XML parsing for message bodies.
 * </p>
 *
 * @author Algomeet Core Team
 * @version 1.1
 */
@Slf4j
public class XmppUtil {
	private static final String DOMAIN_SEPARATOR = "@";
	private static final String BARE_JID_SEPARATOR = "/";
	private static final XMLInputFactory XML_FACTORY = XMLInputFactory.newInstance();
	
	/**
	 * Extracts the localpart (User Key) from a Full or Bare JID.
	 * <p>Example: {@code "arielle@algomeet.com/laptop"} returns {@code "arielle"}.</p>
	 *
	 * @param fullJid The JID to parse.
	 * @return The lower-cased localpart of the JID, or null if input is empty.
	 */
	public static String getUserKey(String fullJid) {
		if(!StringUtils.hasText(fullJid)) {
			return null;
		}
		
		return fullJid.trim().split(DOMAIN_SEPARATOR, 2)[0].trim().toLowerCase();
	}
	
	/**
	 * Extracts the localpart (Room ID) from a MUC JID.
	 * <p>Example: {@code "dev-room@conference.algomeet.com"} returns {@code "dev-room"}.</p>
	 *
	 * @param roomJid The room JID to parse.
	 * @return The localpart representing the unique room identifier.
	 */
	public static String getRoomId(String roomJid) {
		if(!StringUtils.hasText(roomJid)) {
			return null;
		}
		
		return roomJid.trim().split(DOMAIN_SEPARATOR, 2)[0];
	}
	
	/**
	 * Extracts the Bare JID from a Full JID by removing the resourcepart.
	 * <p>Example: {@code "room@conference.domain/nickname"} returns {@code "room@conference.domain"}.</p>
	 *
	 * @param roomJid The full JID of the room or user.
	 * @return The JID without the resourcepart.
	 */
	public static String getRoomBareJid(String roomJid) {
		if(!StringUtils.hasText(roomJid)) {
			return null;
		}
		
		return roomJid.trim().split(BARE_JID_SEPARATOR, 2)[0];
	}
	    
    /**
     * Extracts the 'h' (handled) attribute value from a Stream Management acknowledgment stanza.
     * <p>
     * <b>Note:</b> This uses optimized string manipulation for performance within the 
     * Netty event loop to avoid the overhead of full XML parsing for simple ACK packets.
     * </p>
     *
     * @param xml The raw XML string of the acknowledgment (e.g., {@code <a h='5'/>}).
     * @return The parsed handled count as a {@code long}, or 0 if parsing fails.
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
     * Extracts the text content of the {@code <body>} element from a raw XMPP XML string.
     * <p>
     * Utilizes a StAX {@link XMLStreamReader} for memory-efficient, forward-only parsing, 
     * which is critical for processing large message stanzas in high-concurrency environments.
     * </p>
     * * @param xml The raw XMPP message stanza.
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
     * Generates and transmits a standardized XMPP error frame back to the client.
     * <p>
     * This utility simplifies error signaling by wrapping the parameters into a 
     * {@link StanzaError} and flushing it to the Netty {@link ChannelHandlerContext}.
     * </p>
     *
     * @param ctx       The Netty channel context for the current session.
     * @param id        The original ID of the stanza that caused the error.
     * @param to        The recipient JID for the error.
     * @param from      The sender JID (usually the server domain).
     * @param errorType The {@link XmppErrorType} (e.g., cancel, auth, modify).
     * @param condition The XMPP error condition (e.g., forbidden, item-not-found).
     * @param text      Descriptive error text for debugging.
     */
    public static void sendError(ChannelHandlerContext ctx, String id, String to, String from, XmppErrorType errorType, String condition, String text) {
        StanzaError error = new StanzaError(id, to, from, errorType, condition, text);
        ctx.writeAndFlush(new TextWebSocketFrame(error.toXml()));
    }
}