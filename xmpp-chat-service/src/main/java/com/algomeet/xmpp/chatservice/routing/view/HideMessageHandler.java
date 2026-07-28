package com.algomeet.xmpp.chatservice.routing.view;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.service.HideMucMessageService;
import com.algomeet.xmpp.chatservice.stanza.parser.MessageViewStaxParser;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class HideMessageHandler {	
	private final HideMucMessageService hideMucMessageService;

	/**
	 * Logic to hide a message. Differentiates between MUC rooms and 1-on-1 chats.
	 * * @return A Mono<Void> that completes when the processing and notifications are finished.
	 */
	public Mono<Void> handleHide(ChannelHandlerContext ctx, String id, XmppPrincipal principal, MessageViewStaxParser.ViewItem item){
		if (StringUtils.hasText(item.roomId)) {
			// GROUP CHAT FLOW
			return hideMucMessageService.hideMessageForUser(
					UUID.fromString(principal.getUserKey()), 
					UUID.fromString(item.roomId), 
					UUID.fromString(item.id), 
					principal.getSessionId())
					.then(hideMucMessageService.sendIqResult(id, principal.getUserKey()));
		} else {			
			// DIRECT CHAT FLOW
			// Chain direct sync and IQ response sequentially
			return hideMucMessageService.composeAndSendDirectSync(item.id.trim(), item.peerKey, principal)
					.then(hideMucMessageService.sendIqResult(id, principal.getUserKey()));
		}
	}	
}