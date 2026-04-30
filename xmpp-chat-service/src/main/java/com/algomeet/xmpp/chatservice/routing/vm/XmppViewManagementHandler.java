package com.algomeet.xmpp.chatservice.routing.vm;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.stanza.ViewManagementSyncStanza;
import com.algomeet.xmpp.chatservice.stanza.parser.ViewManagementStaxParser;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.ulid.UlidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Handler responsible for managing "View Management" stanzas.
 * Currently handles the 'hide' action which allows users to remove messages
 * from their own view (Delete for Me) across all their devices.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppViewManagementHandler {
	private final ViewManagementStaxParser viewManagementStaxParser;
	private final XmppUtil xmppUtil;
	private final DomainProperties domainProperties;
	private final XmppArchiveService xmppArchiveService;
	private final ClusterMessagePublisher clusterMessagePublisher;

	/**
	 * Main entry point for processing incoming View Management XML.
	 */
	public void process(ChannelHandlerContext ctx, String xml, XmppPrincipal principal) throws Exception {	
		ViewManagementStaxParser.ParsedIq vmIq = viewManagementStaxParser.parse(xml);

		if (vmIq != null && vmIq.items != null && !vmIq.items.isEmpty()) {
			vmIq.items.forEach(item -> {
				switch (item.action) {
				case "hide":
					handleHide(ctx, principal, item);
					break;
				default:
					// Reject unsupported actions with a standard XMPP error
					xmppUtil.sendError(ctx, vmIq.iqId, principal.getBareJid(), domainProperties.getDomain(), 
							XmppErrorType.CANCEL, XmppErrorConditions.BAD_REQUEST, "Invalid action " + item);
					break;
				}
			});
		}
	}

	/**
	 * Quick check to see if an incoming string belongs to this namespace.
	 */
	public boolean isViewManagementStanza(String xml) {
		return xml.contains("https://algomeet.app/protocol/view-management");	        
	}

	/**
	 * Logic to hide a message. Differentiates between MUC rooms and 1-on-1 chats.
	 */
	private void handleHide(ChannelHandlerContext ctx, XmppPrincipal principal, ViewManagementStaxParser.ViewItem item){
		if (StringUtils.hasText(item.room)) {
			// GROUP CHAT FLOW
			xmppArchiveService.findByMessageId(item.id.trim())
			.<Void>flatMap(message -> {             	

				log.info("Executing hide: Message {} in room {} by user {}", 
						item.id, item.room, principal.getUserKey());

				// Atomic update in MongoDB: add current user key to 'hiddenFromUserKeys'
				return xmppArchiveService.hideMessageForUser(message.getMessageId(), principal.getUserKey())
						.doOnSuccess(success -> {
							// Disseminate the change to user's other devices
							composeAndSendGroupSync(item.id.trim(), item.room, principal);                            
						})
						.then();
			})
			.subscribe(); // Subscription triggers the reactive pipeline execution

		} else {			
			// DIRECT CHAT FLOW
			composeAndSendDirectSync(item.id.trim(), principal);
		}
	}

	/**
	 * Syncs the 'hide' state for 1-on-1 messages to other resources of the user.
	 */
	private void composeAndSendDirectSync(String targetId, XmppPrincipal principal) {
		String id = UUID.randomUUID().toString();

		ViewManagementSyncStanza vmSync = ViewManagementSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.from(principal.getBareJid())
				.to(principal.getBareJid()) // To self (Bare JID) triggers fan-out
				.build();

		String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();		
		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), ulidString, principal.getDomain());

		// Push to the cluster for delivery to all active sessions for this user
		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.CHAT, false, xml, principal);
	}

	/**
	 * Syncs the 'hide' state for MUC messages and archives the sync event if needed.
	 */
	private void composeAndSendGroupSync(String targetId, String roomJid, XmppPrincipal principal) {
		String id = UUID.randomUUID().toString();

		ViewManagementSyncStanza vmSync = ViewManagementSyncStanza.builder()
				.id(id)
				.targetId(targetId)
				.room(roomJid)
				.from(principal.getBareJid())
				.to(roomJid + "/" + principal.getUserKey()) // MUC targeted to the specific user resource
				.build();

		String ulidString = UlidCreator.getMonotonicUlid().toLowerCase();

		String xml = XmppStanzaUtil.insertStanzaId(vmSync.toXml(), ulidString, principal.getDomain());

		// Publish to other active sessions
		clusterMessagePublisher.convertAndSendToUser(id, principal.getUserKey(), principal.getUserKey(), 
				ChatType.GROUPCHAT, false, xml, principal);
	}
}