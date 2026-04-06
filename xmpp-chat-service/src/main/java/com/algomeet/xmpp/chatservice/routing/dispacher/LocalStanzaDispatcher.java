package com.algomeet.xmpp.chatservice.routing.dispacher;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.connection.registry.LocalChannelRegistry;
import com.algomeet.xmpp.chatservice.connection.stream.XmppStreamManagementBuffer;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Dispatches XMPP stanzas to locally connected sessions on the current server node.</p>
 * 
 * <p>The {@code LocalStanzaDispatcher} is the final step in the routing chain for 
 * "Local-to-Local" or "Remote-to-Local" delivery. It retrieves the active Netty 
 * {@link Channel} from the {@link LocalChannelRegistry} and pushes the XML payload 
 * over the WebSocket.</p>
 * 
 * <p><b>Protocol Responsibilities:</b></p>
 * <ul>
 *     <li><b>Reliable Delivery (XEP-0198):</b> Every dispatched stanza is assigned a 
 *         monotonically increasing sequence number ({@code smOutboundH}).</li>
 *     <li><b>Ack Tracking:</b> Stanzas are registered with the {@link XmppStreamManagementBuffer} 
 *         before being flushed, allowing the server to handle potential reconnection 
 *         resumptions or delivery confirmations.</li>
 *     <li><b>Session Validation:</b> Verifies that the target channel is active and 
 *         initialized before attempting transmission.</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalStanzaDispatcher {

	private final LocalChannelRegistry localChannelRegistry; 
	private final XmppStreamManagementBuffer xmppStreamAckTracker;

	/**
	 * Routes a stanza to a specific local user session.
	 * 
	 * @param to          The User Key/ JID (Jabber ID) of the recipient.
	 * @param from        The User Key/ JID of the sender.
	 * @param id          The unique Stanza ID (used for tracking and DB status updates).
	 * @param originalXml The raw XML content to be delivered.
	 */
	public void dispatchLocally(String to, String from, String id, ChatType chatType, String originalXml) {
		Channel targetChannel = localChannelRegistry.getChannel(to);

		if (targetChannel == null || !targetChannel.isActive()) {
			log.debug("Routing failed: No active local channel found for JID: {}", to);
			return;
		}

		if (chatType == ChatType.GROUPCHAT) {			
			// No need to track the SM buffer
			targetChannel.writeAndFlush(new TextWebSocketFrame(originalXml));
		} else {

			// Retrieve the session's outbound counter to maintain protocol sequence
			AtomicLong outboundH = targetChannel.attr(XmppSessionAttributes.SM_OUTBOUND_H_KEY).get();
			if (outboundH != null) { 
				// Write to the WebSocket
				targetChannel.writeAndFlush(new TextWebSocketFrame(originalXml));

				// Track in the SM buffer so we can update DB status when the client eventually acks
				long sequence = outboundH.incrementAndGet();
				xmppStreamAckTracker.track(to, sequence, id);

				log.trace("Stanza {} dispatched to {} with sequence h={}", id, to, sequence);
			} else {
				log.error("Outbound SM counter missing for active channel: {}. Reliability broken.", to);
				// Fallback: send without tracking or close the corrupted session
				targetChannel.writeAndFlush(new TextWebSocketFrame(originalXml));
			}
		}
	}
}