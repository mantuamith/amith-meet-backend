package com.algomeet.xmpp.chatservice.routing;

import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.chat.XmppChatHandler;
import com.algomeet.xmpp.chatservice.routing.discovery.XmppDiscoveryHandler;
import com.algomeet.xmpp.chatservice.routing.mam.XmppMamHandler;
import com.algomeet.xmpp.chatservice.routing.muc.XmppMucHandler;
import com.algomeet.xmpp.chatservice.routing.sm.XmppStreamManagementStanzaHandler;
import com.algomeet.xmpp.chatservice.routing.state.XmppUserGlobalPresenceHandler;
import com.algomeet.xmpp.chatservice.routing.view.XmppViewManageHandler;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * <p>The primary entry point for processing and routing all incoming XMPP stanzas 
 * over WebSocket frames.</p>
 * 
 * <p>This handler manages the high-level orchestration of a stanza's lifecycle:</p>
 * <ul>
 *     <li><b>Validation:</b> Ensures the incoming payload is well-formed XML.</li>
 *     <li><b>Lifecycle Management:</b> Updates presence and chat states (XEP-0085).</li>
 *     <li><b>Stream Management:</b> Handles {@code <r/>} and {@code <a/>} elements for 
 *         XEP-0198 reliability.</li>
 *     <li><b>Direct Routing:</b> Persists messages to {@link OfflineMessageService} and 
 *         broadcasts to the cluster via {@link ClusterMessagePublisher}.</li>
 *     <li><b>Error Handling:</b> Returns standardized XMPP error stanzas for parsing 
 *         or persistence failures.</li>
 * </ul>
 * 
 * <p>This handler is {@link ChannelHandler.Sharable @Sharable}, meaning a single instance 
 * is used across all Netty channels. Per-session state is retrieved via 
 * {@link XmppSessionAttributes}.</p>
 * 
 * @author Algomeet Core Team
 */
@Slf4j
@ChannelHandler.Sharable
@Component
@RequiredArgsConstructor
public class XmppRoutingHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

	private final XmppDiscoveryHandler xmppDiscoveryHandler;
	private final XmppUserGlobalPresenceHandler xmppUserStateHandler;
	private final XmppStreamManagementStanzaHandler xmppStreamManagementHandler;
	private final XmppChatHandler xmppDirectChatHandler;
	private final XmppMucHandler xmppMucHandler;
	private final XmppMamHandler xmppMamHandler;
	private final DomainProperties domainProperties;
	private final XmppUtil xmppUtil;
	private final XmppViewManageHandler xmppViewManagementHandler;

	/**
	 * Unique Netty {@link io.netty.util.AttributeKey} used to attach a thread-safe, 
	 * reactive serialization queue directly to an active socket channel's context.
	 * * <p><b>Architectural Purpose:</b></p>
	 * This key binds a {@link reactor.core.publisher.Sinks.Many} buffer containing lazy 
	 * {@link reactor.core.publisher.Mono} routing tasks to individual connection sessions. 
	 * It acts as a localized FIFO queue that forces all inbound stanzas from a single user 
	 * connection to be processed in strict, chronological order via non-blocking serialization.
	 * * <p><b>Concurrency & Backpressure Control:</b></p>
	 * By isolating this queue per connection, the application guarantees that Message N+1 
	 * will never begin its database execution path until Message N has completely finished. 
	 * This design prevents out-of-order race conditions introduced by the multi-threaded 
	 * database thread pool without locking or blocking Netty's underlying EventLoop selector.
	 */
	private static final io.netty.util.AttributeKey<reactor.core.publisher.Sinks.Many<reactor.core.publisher.Mono<Void>>> CHANNEL_QUEUE_KEY = 
			io.netty.util.AttributeKey.valueOf("xmpp.channel.pipeline.queue");

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// Initialize a serialized, thread-safe queue for this channel when it connects
		reactor.core.publisher.Sinks.Many<reactor.core.publisher.Mono<Void>> queue = 
				reactor.core.publisher.Sinks.many().unicast().onBackpressureBuffer();

		ctx.channel().attr(CHANNEL_QUEUE_KEY).set(queue);

		// Drain the queue sequentially (concatMap guarantees message N+1 never starts until message N completes)
		queue.asFlux()
		.concatMap(taskMono -> taskMono.onErrorResume(e -> reactor.core.publisher.Mono.empty()))
		.subscribe();

		super.channelActive(ctx);
	}
		
	/**
	 * Entry point for incoming WebSocket text frames.
	 * 
	 * @param ctx   The channel context.
	 * @param frame The WebSocket frame containing the XMPP XML string.
	 */
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {  
		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
		String xml = frame.text();

		try {
			// 1. Lifecycle & Presence logic (XEP-0186 / XEP-0085)
			if (principal != null) {
				handleStatusUpdates(ctx, principal, xml);
			}

			// 2. Extract routing metadata without fully unmarshalling the whole stanza
			Map<String, String> attributes = XmppStanzaUtil.parseStanzaAttributes(xml);
			String toJid = attributes.get("to");
			String fromJid = attributes.get("from");
			String id = attributes.get("id");
			String type = attributes.get("type");

			// 3. Handle Missing 'from' Attribute (Server Stamping)
			if(toJid != null) {
				if (fromJid == null) {
					String tempFromJid = principal.getBareJid();

					if (fromJid == null) {
						xml = XmppStanzaUtil.injectFromAttribute(xml, tempFromJid);
						fromJid = tempFromJid;
					} 
				} else {
					// 4. Security: Validate provided 'from' against authorized Bare JID
					String authorizedBareJid = principal.getBareJid();
					boolean isValid = fromJid.regionMatches(true, 0, authorizedBareJid, 0, authorizedBareJid.length());

					if (!isValid) {
						log.warn("Unauthorized 'from' JID attempt: {} by {}", fromJid, authorizedBareJid);
						xmppUtil.sendError(ctx, id, principal.getBareJid(), domainProperties.getDomain(), XmppErrorType.AUTH, 
								XmppErrorConditions.FORBIDDEN, "Invalid from attribute");
						return;
					}
				}
			}
			
			// 5. Assign Message ID if not assigned
			if (!StringUtils.hasText(id)) {
				id = UuidCreator.getTimeOrderedEpoch().toString();
			}

			// 6. Identify MAM once
			boolean mam = isMamRequest(type, xml);

			// 7. Branch based on logic: MAM and Server-directed queries go to InfoQueryHandler
			// Direct/Group messages go to respective handlers

			final String finalXml = xml;
		    final String finalFromJid = fromJid;
		    final String finalId = id;
		    
			if (!mam && (XmppMessageType.GROUPCHAT == XmppMessageType.fromString(type) || isGroupChat(toJid))) {				
				// Apply sequential backpressure for room chats			    
			    Mono<Void> mucTask = Mono.defer(() -> {
			        ctx.channel().config().setAutoRead(false);
			        return xmppMucHandler.handleGroupChatRouting(ctx, finalId, toJid, finalFromJid, type, finalXml);
			    }).doFinally(signal -> {
			        ctx.channel().config().setAutoRead(true);
			        ctx.read();
			    });

			    reactor.core.publisher.Sinks.Many<Mono<Void>> queue = ctx.channel().attr(CHANNEL_QUEUE_KEY).get();
			    if (queue != null) {
			        queue.tryEmitNext(mucTask);
			    } else {
			        mucTask.subscribe();
			    }

			} else if (!mam && StringUtils.hasText(toJid)) {
				// Apply sequential backpressure for direct 1:1 chats					
				Mono<Void> routingTask = Mono.defer(() -> {
					// Toggle read suspension *only* when this message reaches the front of the queue
					ctx.channel().config().setAutoRead(false);
					return xmppDirectChatHandler.handleDirectChatRouting(ctx, finalId, toJid, finalFromJid, type, finalXml);
				})
				.doFinally(signal -> {
					// Restore channel availability immediately on termination metrics
					ctx.channel().config().setAutoRead(true);
					ctx.read(); 
				});

				// Feed it into this specific connection's queue for serialized execution
				reactor.core.publisher.Sinks.Many<Mono<Void>> queue = ctx.channel().attr(CHANNEL_QUEUE_KEY).get();
				if (queue != null) {
					queue.tryEmitNext(routingTask);
				} else {
					// Fallback if channel initialization was skipped
					routingTask.subscribe();
				}
			} else {
				
				// This block catches MAM, Service Discovery, and Stream Management
				if (xmppStreamManagementHandler.isStreamManagementStanza(xml)) {
					xmppStreamManagementHandler.process(ctx, xml, principal);
					
				} else if (mam) {
					// XEP-0313: Message Archive Management
					xmppMamHandler.handleMamRequest(ctx, toJid, xml);
					
				} else if (xmppViewManagementHandler.isMessageViewManagementStanza(xml)) {
					// Handle hiding of messages and etc. 
					xmppViewManagementHandler.process(ctx, xml, principal);
					
				} else {
					xmppDiscoveryHandler.handleQuery(ctx, xml);
				}     
			}

		} catch (XMLStreamException e) {
			log.error("Malformed XML received: {} , {}", xml, e.getMessage());
			xmppUtil.sendError(ctx, principal.getBareJid(), principal.getBareJid(), domainProperties.getDomain(), XmppErrorType.CANCEL,
					XmppErrorConditions.NOT_WELL_FORMED, "XML parsing failed");
		} catch (Exception e) {
			log.error("Routing error for XML {}: {}", xml, e.getMessage(), e);
		}
	}

	private boolean isMamRequest(String type, String xml) {
		return (XmppMessageType.SET == XmppMessageType.fromString(type) 
				&& xml.contains("urn:xmpp:mam:2"));
	}

	/**
	 * Delegates status, presence, and chat state updates to the lifecycle handler.
	 */   
	private void handleStatusUpdates(ChannelHandlerContext ctx, XmppPrincipal principal, String xml) {
		xmppUserStateHandler.processPresence(ctx, principal, xml);
	}  

	public boolean isGroupChat(String to) {        
		// Check if the 'to' address contains a known MUC service domain
		// (This is useful if the stanza is malformed but the routing is correct)
		if (to != null && to.contains("@" + domainProperties.getGroupChatDomain())) {
			return true;
		}

		return false;
	}
}