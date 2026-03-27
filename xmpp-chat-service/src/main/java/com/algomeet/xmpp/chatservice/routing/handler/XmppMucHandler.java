package com.algomeet.xmpp.chatservice.routing.handler;

import org.springframework.stereotype.Component;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.parser.GroupChatParser;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import io.netty.channel.ChannelHandlerContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@AllArgsConstructor
public class XmppMucHandler {
	private final XmppArchiveService xmppArchiveService;
	private final GroupClient groupClient;

	/**
	 * Handles routing for Multi-User Chat rooms.
	 * Strategy: Save every event (Message, Reaction, Retraction) and forward to client.
	 */
	public void handleGroupChatRouting(ChannelHandlerContext ctx, String id, String roomJid, String from, String originalXml) {

		// Parse the XML into StanzaInfo (Detects stanzaType, category, targetId)
		StanzaInfo info = GroupChatParser.parse(originalXml);

		// Process Archiving for all other events (message, reaction, retraction)
		String roomId = XmppUtil.getRoomId(roomJid);

		// Presence Check: Filter out <presence> from the archive
		if ("presence".equals(info.getStanzaType())) {
			log.debug("Presence received from {}. Skipping database archive.", from);
			// Optional: updateRedisStatus(from, info);

		} else {
			
			// Pass the internal ID (MAM ID) and parsed info to the service
			xmppArchiveService.archiveEvent(originalXml, info, roomId, from)
			.doOnSuccess(saved -> log.debug("Event archived [{}]: category={}", id, info.getCategory()))
			.doOnError(e -> log.error("Archive failed: {}", e.getMessage()))
			.subscribe();
		}

		// Fetch Group Metadata & Handle Live Routing
		try {
			// This could be used to verify the sender is a member before broadcasting
			MucRoomDto group = groupClient.getGroupById(Long.parseLong(roomId));

			// Logic to forward the 'originalXml' to other connected Netty channels (Live Users)
			// broadcastLive(group, originalXml);

		} catch (NumberFormatException e) {
			log.error("Invalid roomId format: {}", roomId);
		}
	}
}