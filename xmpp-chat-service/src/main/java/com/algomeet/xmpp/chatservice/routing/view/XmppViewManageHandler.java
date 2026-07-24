package com.algomeet.xmpp.chatservice.routing.view;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.constant.Constants;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.stanza.parser.MessageViewStaxParser;
import com.algomeet.xmpp.chatservice.util.XmppUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Handler responsible for managing "View Management" stanzas.
 * Currently handles the 'hide' action which allows users to remove messages
 * from their own view (Delete for Me) across all their devices.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppViewManageHandler {
	private final MessageViewStaxParser viewManagementStaxParser;
	private final XmppUtil xmppUtil;
	private final DomainProperties domainProperties;
	private final HideMessageHandler hideMessageHandler;
	
	/**
	 * Main entry point for processing incoming View Management XML.
	 */
	public Mono<Void> process(ChannelHandlerContext ctx, String xml, XmppPrincipal principal) {	
		return Mono.defer(() -> {
			try {
				MessageViewStaxParser.ParsedIq vmIq = viewManagementStaxParser.parse(xml);

				if (vmIq == null || vmIq.items == null || vmIq.items.isEmpty()) {
					return Mono.empty();
				}

				switch (vmIq.items.get(0).action) {
				case "hide":
					// 1. Convert collection to a single managed Reactive Pipeline
					return Flux.fromIterable(vmIq.items)
							.flatMap(item -> {
								if ("hide".equals(item.action)) {
									// Return the mono to process items concurrently (up to 16 at a time)
									return hideMessageHandler.handleHide(ctx, vmIq.iqId, principal, item);
								} else {
									return Mono.error(new IllegalArgumentException("Invalid action " + item.action));
								}
							}, 16) 
							// 2. Threading isolated safely to one root configuration boundary
							.subscribeOn(Schedulers.boundedElastic())
							.doOnError(err -> {
								log.error("Failed to process message hide actions for user {}", principal.getUserKey(), err);
								xmppUtil.sendError(ctx, vmIq.iqId, principal.getBareJid(), domainProperties.getDomain(), 
										XmppErrorType.WAIT, XmppErrorConditions.INTERNAL_SERVER_ERROR, "Processing failed");
							})
							.then();			
									
				default:
					// Reject unsupported actions with a standard XMPP error
					xmppUtil.sendError(ctx, vmIq.iqId, principal.getBareJid(), domainProperties.getDomain(), 
							XmppErrorType.CANCEL, XmppErrorConditions.BAD_REQUEST, "Invalid action " + vmIq.items.get(0).action);
					return Mono.empty();
				}
			} catch (Exception e) {
				log.error("Stanza parsing error in ViewManageHandler for user {}", principal.getUserKey(), e);
				return Mono.error(e);
			}
		});
	}
	
	/**
	 * Quick check to see if an incoming string belongs to this namespace.
	 */
	public boolean isMessageViewManagementStanza(String xml) {
		// Small adjustment to avoid false matches on casual chat text
		return xml.contains("xmlns=\"" + Constants.NS_MESSAGE_VIEW + "\"") 
				|| xml.contains("xmlns='" + Constants.NS_MESSAGE_VIEW + "'");	        
	}
}