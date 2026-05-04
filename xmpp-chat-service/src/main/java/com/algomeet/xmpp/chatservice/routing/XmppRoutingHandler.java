package com.algomeet.xmpp.chatservice.routing;

import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
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
import com.algomeet.xmpp.chatservice.routing.vm.XmppViewManagementHandler;
import com.algomeet.xmpp.chatservice.service.OfflineMessageService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
	private final XmppViewManagementHandler xmppViewManagementHandler;

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
						xml = injectFromAttribute(xml, tempFromJid);
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

			// 5. Identify MAM once
			boolean mam = isMamRequest(type, xml);

			// 6. Branch based on logic: MAM and Server-directed queries go to InfoQueryHandler
			// Direct/Group messages go to respective handlers

			if (!mam && (XmppMessageType.GROUPCHAT == XmppMessageType.fromString(type) || isGroupChat(toJid))) {
				xmppMucHandler.handleGroupChatRouting(ctx, id, toJid, fromJid, type, xml);

			} else if (!mam && StringUtils.hasText(toJid)) {
				xmppDirectChatHandler.handleDirectChatRouting(ctx, id, toJid, fromJid, type, xml);

			} else {
				
				// This block catches MAM, Service Discovery, and Stream Management
				if (xmppStreamManagementHandler.isStreamManagementStanza(xml)) {
					xmppStreamManagementHandler.process(ctx, xml, principal);
				} else if (mam) {
					// XEP-0313: Message Archive Management
					xmppMamHandler.handleMamRequest(ctx, toJid, xml);
				} else if (xmppViewManagementHandler.isViewManagementStanza(xml)) {
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

	/**
	 * Safely injects the 'from' attribute into the first XML tag.
	 */
	private String injectFromAttribute(String xml, String jid) {
		String replacement = String.format(" from='%s'", jid);
		// Find the end of the first tag name (either a space or the end of the tag '>')
		int firstSpace = xml.indexOf(' ');
		int firstTagEnd = xml.indexOf('>');

		int insertAt = (firstSpace != -1 && firstSpace < firstTagEnd) ? firstSpace : firstTagEnd;

		return new StringBuilder(xml)
				.insert(insertAt, replacement)
				.toString();
	}    
}