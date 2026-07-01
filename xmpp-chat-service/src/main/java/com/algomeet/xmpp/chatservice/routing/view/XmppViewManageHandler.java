package com.algomeet.xmpp.chatservice.routing.view;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.stanza.parser.ViewManageStaxParser;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler responsible for managing "View Management" stanzas.
 * Currently handles the 'hide' action which allows users to remove messages
 * from their own view (Delete for Me) across all their devices.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppViewManageHandler {
	private final ViewManageStaxParser viewManagementStaxParser;
	private final XmppUtil xmppUtil;
	private final DomainProperties domainProperties;
	private final HideMessageHandler hideMessageHandler;
	private final PinMessageHandler pinMessageHandler;
	
	/**
	 * Main entry point for processing incoming View Management XML.
	 */
	public void process(ChannelHandlerContext ctx, String xml, XmppPrincipal principal) throws Exception {	
		ViewManageStaxParser.ParsedIq vmIq = viewManagementStaxParser.parse(xml);

		if (vmIq != null && vmIq.items != null && !vmIq.items.isEmpty()) {
			vmIq.items.forEach(item -> {
				switch (item.action) {
				case "hide":
					hideMessageHandler.handleHide(ctx, vmIq.iqId, principal, item);
					break;
					
				case "pin":
					pinMessageHandler.handlePin(ctx, vmIq.iqId, principal, item);
					break;
					
				case "unpin":
					pinMessageHandler.handlePin(ctx, vmIq.iqId, principal, item);
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
	public boolean isMessageViewManagementStanza(String xml) {
		return xml.contains("https://algomeet.app/protocol/view-management");	        
	}
}