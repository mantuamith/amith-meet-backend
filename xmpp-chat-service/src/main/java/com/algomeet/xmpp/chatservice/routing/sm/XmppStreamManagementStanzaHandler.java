package com.algomeet.xmpp.chatservice.routing.sm;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.stream.XMLStreamException;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;
import com.algomeet.xmpp.chatservice.util.XmppSmRedisUtil;
import com.algomeet.xmpp.chatservice.util.XmppSmSessionUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
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
public class XmppStreamManagementStanzaHandler {
	private final XmppStreamManagementOutboundBuffer xmppStreamAckTracker;
	private final OfflineMessageService offlineMessageService; 
	private final XmppSmRedisUtil xmppSmRedisUtil;


	private static final String NS = "urn:xmpp:sm:3";
	/**
	 * Processes incoming Stream Management XML elements.
	 * 
	 * @param ctx       The Netty {@link ChannelHandlerContext}.
	 * @param xml       The raw XML string (either {@code <r/>} or {@code <a/>}).
	 * @param principal The authenticated user session.
	 */
	public void process(ChannelHandlerContext ctx, String xml, XmppPrincipal principal) {	
		if (isStreamManagementReq(xml)) {                        
			// SM must be explicitly enabled for the session (<enable xmlns='urn:xmpp:sm:3'/>)
			AtomicBoolean isEnabledSM =
					ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_ENABLED_KEY).get();

			// If SM is active, we increment the inbound sequence counter (h)
			if (isEnabledSM != null && isEnabledSM.get()) {

				// The client is requesting an 'h' value from the server
				AtomicLong handledCount = ctx.channel().attr(XmppSessionAttributes.SM_INBOUND_H_KEY).get();
				Long h = handledCount != null ? handledCount.get() : 0;
				ctx.writeAndFlush(new TextWebSocketFrame(new StreamAck(h).toXml()));
				log.trace("Responded to ack request from {} with h={}", principal.getUserKey(), h);
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
			boolean resumeRequested = Boolean.valueOf(XmppStanzaUtil.getAttribute(xml, "resume"));

			// 2. Generate a unique SM ID if resumption is enabled
			String smId = resumeRequested ? UUID.randomUUID().toString() : null;

			// 3. Initialize Stream Management Counters (XEP-0198)       
			XmppSmSessionUtil.initSmSession(ctx, resumeRequested, smId, 0L);

			// 4. Build the <enabled /> response
			StringBuilder response = new StringBuilder("<enabled xmlns='urn:xmpp:sm:3'");
			if (smId != null) {
				response.append(String.format(" id='%s'", smId));
			}
			response.append(String.format(" resume='%b'/>", resumeRequested));

			// 5. Send the confirmation back to the client
			ctx.writeAndFlush(new TextWebSocketFrame(response.toString()));

			log.debug("Stream Management enabled for session. Resumable: {}", resumeRequested);

		} else if(xml.contains("<resume")) {    			
			String smId = XmppStanzaUtil.getAttribute(xml, "previd");

			xmppSmRedisUtil.getLastAck(smId)
			.doOnNext(lastAck -> {
				if (lastAck > 0) {						
					// Initialize Stream Management Counters (XEP-0198)       
					XmppSmSessionUtil.initSmSession(ctx, true, smId, lastAck);

					// Send response
					sendResumeResponse(ctx, lastAck);

					// Value exists → can attempt resume
					log.debug("Found lastAck={}, attempting resume", lastAck);
				} else {
					sendResumeFailed(ctx);
					// No value → fresh session
					log.debug("No SM state found, starting new session");
				}
			})
			.subscribe();      
		}
	}

	/**
	 * Sends XEP-0198 <resume/> response to client.
	 *
	 * @param ctx Netty channel context
	 * @param lastAck last acknowledged h value from server state
	 */
	public static void sendResumeResponse(ChannelHandlerContext ctx, long lastAck) {

		StringBuilder response = new StringBuilder();
		response.append("<resume xmlns='").append(NS).append("'");

		response.append(String.format(" h='%d'/>", lastAck));
		ctx.writeAndFlush(new TextWebSocketFrame(response.toString()));
	}

	/**
	 * Sends XEP-0198 <failed/> response when session resumption is not possible.
	 *
	 * Cases:
	 * - session expired (TTL in Redis expired)
	 * - previd mismatch
	 * - missing SM state
	 */
	public static void sendResumeFailed(ChannelHandlerContext ctx) {
		String response = "<failed xmlns='" + NS + "'/>";
		ctx.writeAndFlush(new TextWebSocketFrame(response));
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