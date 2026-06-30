package com.algomeet.xmpp.chatservice.util;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.enums.ViewManageEnum;
import com.algomeet.xmpp.chatservice.routing.view.PinMessageHandler;
import com.algomeet.xmpp.chatservice.routing.view.UnpinMessageHandler;
import com.algomeet.xmpp.chatservice.stanza.parser.PinMessageStaxParser;
import com.algomeet.xmpp.chatservice.stanza.parser.PinMessageStaxParser.ParsedMessage;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class PinMessageUtil {
	private final PinMessageHandler pinMessageHandler;
	private final PinMessageStaxParser pinMessageStaxParser;
	private final UnpinMessageHandler unpinMessageHandler;

	public void handlePinOrUnpinChatMessage(String receiverKey, String xml, XmppPrincipal principal) {
		try {
			ParsedMessage message = pinMessageStaxParser.parse(xml);
			if (ViewManageEnum.PIN.getValue().equals(message.action)) {
				pinMessageHandler.handlePinChatMessageForEveryone(receiverKey, message, principal);
				
			} else if(ViewManageEnum.UNPIN.getValue().equals(message.action)) {
				unpinMessageHandler.handleUnpinChatMessageForEveryone(receiverKey, message, principal);
			}

		} catch(Exception ex) {
			log.error("Error parsing pin stanza {} pin/unpin by {}", xml, principal.getUserKey(), ex);
		}		
	}

	public void handlePinOrUnpinGroupMessage(String groupId, String xml, XmppPrincipal principal) {
		try {
			ParsedMessage message = pinMessageStaxParser.parse(xml);
			if (ViewManageEnum.PIN.getValue().equals(message.action)) {
				pinMessageHandler.handlePinGroupMessageForEveryone(groupId, message, principal);
				
			} else if(ViewManageEnum.UNPIN.getValue().equals(message.action)) {
				unpinMessageHandler.handleUnpinGroupMessageForEveryone(groupId, message, principal);
			}


		} catch(Exception ex) {
			log.error("Error parsing pin stanza {} pin/unpin by {}", xml, principal.getUserKey(), ex);
		}		
	}

}
