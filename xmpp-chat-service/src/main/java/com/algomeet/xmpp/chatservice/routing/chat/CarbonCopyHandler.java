package com.algomeet.xmpp.chatservice.routing.chat;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import lombok.RequiredArgsConstructor;

/**
 * Handles Message Carbon synchronization for multi-device users.
 *
 * <p>This component implements the sender-side behavior of
 * XEP-0280: Message Carbons.</p>
 *
 * <p>When a user sends a message from one active device
 * (mobile, web, desktop, tablet, etc.), a carbon copy is generated
 * and delivered to the user's other connected sessions so all devices
 * remain synchronized.</p>
 *
 * <p>Example:</p>
 * <ul>
 *     <li>User sends message from mobile phone</li>
 *     <li>Desktop client receives a &lt;sent/&gt; carbon copy</li>
 *     <li>Web client also receives the same synchronized copy</li>
 * </ul>
 *
 * <p>This improves cross-device consistency and conversation continuity.</p>
 */
@Component
@RequiredArgsConstructor
public class CarbonCopyHandler {

    /**
     * Utility used to build normalized JIDs such as bare JIDs.
     */
    private final JidUtil jidUtil;

    /**
     * Performs final local session fan-out to connected sockets
     * on the current node.
     */
    private final LocalStanzaDispatcher localStanzaDispatcher;

    /**
     * Creates and dispatches a sender carbon copy for the user's
     * other active sessions.
     *
     * <p>The generated stanza uses:</p>
     * <ul>
     *     <li>XEP-0280 {@code <sent/>}</li>
     *     <li>XEP-0297 {@code <forwarded/>}</li>
     * </ul>
     *
     * <p>The originating session is excluded to prevent the same device
     * from receiving a duplicate echo of the message it just sent.</p>
     *
     * @param fromUserKey   Internal user key of the sender.
     * @param userSessionId Session ID of the device that originally sent the message.
     *                      This session is excluded from carbon delivery.
     * @param sentStanza    Original outgoing XMPP {@code <message/>} stanza.
     */
    public void handleSentMessageCarbonCopy(
            String fromUserKey,
            String userSessionId,
            String sentStanza,
            boolean shouldCarbon) {
    	
    	// Validate null value
    	if (!(shouldCarbon)
    			|| !(StringUtils.hasText(fromUserKey)) 
    			|| "null".equalsIgnoreCase(fromUserKey.trim())) {
    		return;
    	}

        /**
         * Build sender carbon wrapper.
         *
         * Structure:
         *
         * <message>
         *   <sent xmlns='urn:xmpp:carbons:2'>
         *     <forwarded xmlns='urn:xmpp:forward:0'>
         *        original message stanza
         *     </forwarded>
         *   </sent>
         * </message>
         *
         * Both 'from' and 'to' are the sender's bare JID because the
         * carbon is routed internally to the same account's other devices.
         */
        String carbonPayload = String.format(
                "<message xmlns='jabber:client' from='%s' to='%s' type='chat'>" +
                        "<sent xmlns='urn:xmpp:carbons:2'>" +
                        "<forwarded xmlns='urn:xmpp:forward:0'>%s</forwarded>" +
                        "</sent>" +
                        "</message>",
                jidUtil.getBareJid(fromUserKey),
                jidUtil.getBareJid(fromUserKey),
                sentStanza
        );

        /**
         * Deliver carbon locally to all active sessions of this user
         * except the originating session.
         *
         * Parameters:
         * 1. Unique internal routing ID
         * 2. Target user key
         * 3. allowEcho = false (skip same sender session)
         * 4. session to exclude
         * 5. generated carbon stanza
         */
        localStanzaDispatcher.dispatchLocally(
                UUID.randomUUID().toString(),
                fromUserKey,
                false,
                userSessionId,
                carbonPayload
        );
    }
}