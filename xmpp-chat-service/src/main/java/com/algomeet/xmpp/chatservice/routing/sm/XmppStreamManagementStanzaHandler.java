package com.algomeet.xmpp.chatservice.routing.sm;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.service.CallSessionRecoveryService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.StreamAck;
import com.algomeet.xmpp.chatservice.util.XmppSmSessionRedisUtil;
import com.algomeet.xmpp.chatservice.util.XmppSmSessionUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;  // <--- Singular
import reactor.core.scheduler.Schedulers; // <--- Singular package, Plural class

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
	private final XmppSmSessionRedisUtil xmppSmRedisUtil;
	private final CallSessionRecoveryService callSessionRecoveryService;


	private static final String NS = "urn:xmpp:sm:3";
	/**
	 * Processes incoming Stream Management XML elements.
	 * 
	 * @param ctx       The Netty {@link ChannelHandlerContext}.
	 * @param xml       The raw XML string (either {@code <r/>} or {@code <a/>}).
	 * @param principal The authenticated user session.
	 */
	
	// Fixed package resolution path here
    private static final Scheduler SM_WORKER_SCHEDULER = 
            Schedulers.newBoundedElastic(100, 5000, "xmpp-sm-workers");
	
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
		} else {
			processSmEnable(ctx, xml, principal);
		}
	}

	public void processSmEnable(ChannelHandlerContext ctx, String xml, XmppPrincipal principal) {
		if (xml.contains("<enable")) {  			

			// 1. Extract the client's requested 'resume' preference (default to false if not found)
			boolean resumeRequested = Boolean.valueOf(XmppStanzaUtil.getAttribute(xml, "resume"));

			// 2. Generate a unique SM ID if resumption is enabled
			String smId = resumeRequested ? UUID.randomUUID().toString() : null;

			// 3. Initialize Stream Management Counters (XEP-0198)       
			XmppSmSessionUtil.initSmSession(ctx, resumeRequested, smId, 0L);

			// 4. Create redis entry for SM session and build the <enabled /> response
			StringBuilder response = new StringBuilder("<enabled xmlns='urn:xmpp:sm:3'");
			if (smId != null) {				
				// Add SM Id in the response
				response.append(String.format(" id='%s'", smId));
			}
			
			response.append(String.format(" resume='%b'/>", resumeRequested));

			// 5. Send the confirmation back to the client
			ctx.writeAndFlush(new TextWebSocketFrame(response.toString()));

			log.debug("Stream Management enabled for session. Resumable: {}", resumeRequested);

		} else if (xml.contains("<resume")) {
		    // Extract required attributes for XEP-0198 stream resumption
		    String prevId = XmppStanzaUtil.getAttribute(xml, "previd");
		    
		    log.debug("Received <resume /> for previd: {} with client-h: {}", prevId, XmppStanzaUtil.getAttribute(xml, "h"));
		    
		    // NON-BLOCKING BARRIER: Turn off auto-read on the TCP socket channel.
	        // This stops incoming stanzas from interleaving without freezing the execution thread.
	        ctx.channel().config().setAutoRead(false);

		    /**
		     * STRATEGY: Sequence Alignment & State Restoration
		     * * We use a blocking call here as a "Barrier" pattern. In a Netty pipeline, 
		     * subsequent stanzas (e.g., Jingle candidates) might already be in the 
		     * TCP buffer. We must restore the 'h' counter and re-bind the session 
		     * before the next handler in the pipeline attempts to process them.
		     */
	        // Example usage in your Resumption Handler
		    String userKey = principal.getBareJid(); // Get the owner of the session
		    
		    xmppSmRedisUtil.getSmSessionData(prevId)
	        .subscribeOn(SM_WORKER_SCHEDULER) // Offload I/O lookup off the Netty worker thread
		    .filter(sessionMap -> !sessionMap.isEmpty()) 
		    // Type Hint <sessionMap> ensures the compiler knows the final return type of the flatMap
		    .<Long>flatMap(sessionMap -> {   
		    	   Long lastAck = Long.parseLong(sessionMap.get(XmppSmSessionRedisUtil.FIELD_H).toString());
		    	   String prevUserSessionId = sessionMap.get(XmppSmSessionRedisUtil.FIELD_USER_SESSION_ID).toString();
		    	   log.info("Resume connection of previous user session ID: {}, h: {}", prevUserSessionId, lastAck);
		    	   		    	   	    	   
		    	   /**
		            * Marks the current Netty channel session as successfully resumed
		            * under Stream Management (XEP-0198).
		            *
		            * This indicates that the client has reconnected using a valid
		            * previous SM session (previd) and the server has accepted the
		            * resumption request.
		            *
		            * Effects of setting this flag:
		            * - Enables replay continuation of buffered stanzas (if any)
		            * - Differentiates resumed session from a fresh login session
		            * - Helps prevent duplicate processing of previously acknowledged stanzas
		            *
		            * Note:
		            * This is stored as a channel-level attribute and is only valid
		            * for the lifetime of the active connection.
		            */
		           ctx.channel()
		               .attr(XmppSessionAttributes.SM_RESUMPTION_SUCCESS_KEY)
		               .set(new AtomicBoolean(true));
		            // 1. Re-bind to local Netty context	
		            XmppSmSessionUtil.initSmSession(ctx, true, prevId, lastAck);
		            
		            // 2. Resume dropped call:
		            callSessionRecoveryService.updateSessionRebind(prevUserSessionId, principal.getSessionId()).subscribe();

		            // 3. Update mapping to the NEW WebSocket/Netty session ID
		            return xmppSmRedisUtil.updateUserSessionId(prevId, principal.getSessionId())
		                .thenReturn(lastAck);
		        })
		        .switchIfEmpty(Mono.defer(() -> {
		            log.warn("Resumption failed for user {} with previd {}", userKey, prevId);
		            sendResumeFailed(ctx);
		            return Mono.empty();
		        }))
		        .doOnNext(lastAck -> {
		            sendResumeResponse(ctx, lastAck);
		        })
		        .doFinally(signalType -> {
	                // RE-ENABLE READS: Once processing is complete (success, failure, or cancel),
	                // turn auto-read back on to flush waiting downstream stanzas.
	                ctx.channel().config().setAutoRead(true);
	                ctx.read(); // Explicitly request a fresh read pass 
	            })
		        /**
		         * .block() is used intentionally here.
		         * By blocking the current Netty thread, we prevent "Stanza Interleaving" 
		         * where a message might be processed by a downstream handler before 
		         * the Stream Management session is officially 'resumed'.
		         */
		        .block(); 
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