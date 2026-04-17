package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.MucRole;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.presence.DirectedPresenceBuilder;
import com.algomeet.xmpp.chatservice.stanza.presence.MucUserPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucStateUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for managing and distributing user presence updates within 
 * Multi-User Chat (MUC) environments.
 * * <p>This service manages the complex relationship between global user availability 
 * and specific room rosters, ensuring that occupants in a MUC always see 
 * synchronized presence for all participants.</p>
 * * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucPresenceService {
	private final UserSessionRegistry userSessionRegistry;
	private final DomainProperties domainProperties;
	private final JidUtil jidUtil;

	private final GroupClient groupClient;
	private final MucMessageRouter mucMessageRouter;

	/**
	 * Broadcasts the current user's status change to all participants in every MUC 
	 * room they are currently joined in.
	 * * @param ctx       The Netty context for the active connection.
	 * @param principal The authenticated user identity.
	 * @param newState  The new state to broadcast (ACTIVE, AWAY, etc.).
	 */
	public void broadcastPresenceToAllJoinedGroups(ChannelHandlerContext ctx, String userkey, UserState newState) {    
		try {
			// 1. Fetch all rooms the user is currently a member of
			List<MucRoomDto> groups = groupClient.getGroupsForUserKey(userkey);

			if (!CollectionUtils.isEmpty(groups)) {
				for (MucRoomDto group : groups) {
					broadcastPresenceToGroupParticipants(ctx, userkey, group, newState);
				}
			}
		} catch (Exception ex) {
			log.error("Error broadcasting MUC presence for user key: {}", userkey, ex);
		}
	}

	public void broadcastPresenceToGroupParticipants(ChannelHandlerContext ctx, String userkey, MucRoomDto group, UserState newState) {    
		try {
			String roomJid = jidUtil.getGroupBareJid(group.getId());

			// 2. Identify the sender's membership details for specific room metadata (nickname)
			Optional<MucMember> senderMucMember = group.getMembers().stream()
					.filter(m -> m.getUserKey().equals(userkey))
					.findFirst();

			if (senderMucMember.isPresent()) {
				// 3. Delegate to event handler to push XML stanzas to all room occupants
				// 2. Broadcast the joiner's availability to all members in the room.
				// This includes updating the joiner's view of existing members (Synchronizing State).     


				String presenceXml = MucUserPresenceBuilder
						.create()
						.from(roomJid, senderMucMember.get().getUserKey()) // Resource-part is the member's room identity
						.show(newState.name().toString().toLowerCase())
						.affiliation(senderMucMember.get().getRole())
						.role(MucRole.fromString(senderMucMember.get().getRole()).getValue())
						.build();

				mucMessageRouter.broadcastToOccupants(UUID.randomUUID().toString(), senderMucMember.get().getUserKey(), group, presenceXml, false);
			}
		} catch (Exception ex) {
			log.error("Error broadcasting MUC presence for user key: {}", userkey, ex);
		}
	}


	public void pushGroupParticipantsPresenceToUser(ChannelHandlerContext ctx, String groupId, XmppPrincipal principal) {  
		pushGroupParticipantsPresenceToUser(ctx, groupId, principal.getUserKey());
	}

	public void pushGroupParticipantsPresenceToUser(ChannelHandlerContext ctx, String groupId, String userKey) {    
		try {
			// Fetch group the user belongs to
			MucRoomDto group = groupClient.getGroupById(groupId);
   
			// Push to the group
			pushGroupParticipantsPresenceToUser(ctx, group, userKey);
		} catch (Exception ex) {
			log.error("Error syncing MUC participant presence for user key: {}", userKey, ex);
		}
	}

	public void pushGroupParticipantsPresenceToUser(ChannelHandlerContext ctx, MucRoomDto group, String userKey) { 
		try {	
			// If member is empty exit
			if (CollectionUtils.isEmpty(group.getMembers())) {
				return;
			}

			// Check if user belongs to the group
			Optional<MucMember> userMucInfoOpt = group.getMembers().stream()
					.filter(m -> m.getUserKey().equalsIgnoreCase(userKey))
					.findFirst();

			if (userMucInfoOpt.isEmpty()) {
				log.error("Error pushing group participants presence to user key: {} not belong to group Id: {}", userKey, group.getId());
				return;
			}

			String roomJid = jidUtil.getGroupBareJid(group.getId());

			// 2. Collect all member keys for batch processing
			List<String> memberKeys = group.getMembers().stream()
					.map(MucMember::getUserKey)
					.toList();

			// 3. Perform a single batch-fetch from Redis for all members in this room
			Map<String, Set<UserSession>> allSessionsMap = userSessionRegistry.getAllSessions(memberKeys);

			String toJid = jidUtil.getBareJid(userKey);
					
			for (MucMember member : group.getMembers()) {
				if (member.getUserKey().equalsIgnoreCase(userKey) ) {
					continue;
				}
				
				Set<UserSession> sessions = allSessionsMap.getOrDefault(member.getUserKey(), Collections.emptySet());

				UserState newState = UserState.GONE;
				long updatedAt = 0L;

				// 4. Multi-device arbitration for the room occupant
				if (!sessions.isEmpty()) {
					newState = MucStateUtil.determineOverallState(sessions);
					updatedAt = sessions.stream()
							.mapToLong(UserSession::getUpdatedAt)
							.max()
							.orElse(0L);
				}

				// 5. Build a Group-Specific Presence Stanza (XEP-0045)
				String presenceXml = MucUserPresenceBuilder
						.create()
						.from(roomJid, member.getUserKey()) // Resource-part is the member's room identity
						.to(toJid)      // Delivered to the newly connected user
						.status(newState.name().toString().toLowerCase())
						.updatedAt(updatedAt != 0 ? Instant.ofEpochMilli(updatedAt).toString() : null)
						.affiliation(member.getRole())
						.role(MucRole.fromString(member.getRole()).getValue())
						.build();

				// Send to the Netty outbound pipeline
				ctx.writeAndFlush(new TextWebSocketFrame(presenceXml));
			}		            
		} catch (Exception ex) {
			log.error("Error syncing MUC participant presence for user key: {}", userKey, ex);
		}
	}
}