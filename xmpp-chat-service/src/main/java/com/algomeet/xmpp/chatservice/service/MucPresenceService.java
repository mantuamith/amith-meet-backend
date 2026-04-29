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
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.presence.MucUserPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.UserStateUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for managing and distributing user presence updates within 
 * Multi-User Chat (MUC) environments.
 * <p>
 * This service ensures that distributed presence states (Active, Inactive, etc.) 
 * stored in Redis are synchronized across MUC room rosters. It plays a critical role 
 * in resolving "zombie" session data to ensure occupants see an accurate, real-time 
 * view of participant availability.
 * </p>
 * * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MucPresenceService {
	private final UserSessionRegistry userSessionRegistry;
	private final JidUtil jidUtil;

	private final GroupClient groupClient;
	private final MucMessageRouter mucMessageRouter;
	private final LocalStanzaDispatcher localStanzaDispatcher;

	/**
	 * Broadcasts the current user's status change to all participants in every MUC 
	 * room they are currently joined in.
	 * * @param ctx       The Netty context for the active connection.
	 * @param userkey   The unique identifier of the user updating their state.
	 * @param newState  The new presence state (ACTIVE, AWAY, etc.) to push to all groups.
	 */
	public void broadcastPresenceToAllJoinedGroups(ChannelHandlerContext ctx, String userkey, UserState newState) {    
		try {
			// 1. Fetch all room memberships to identify broadcast targets
			List<MucRoomDto> groups = groupClient.getGroupsForUserKey(userkey);

			if (!CollectionUtils.isEmpty(groups)) {
				for (MucRoomDto group : groups) {
					broadcastPresenceToGroupParticipants(ctx, userkey, group, newState);
				}
			}
		} catch (Exception ex) {
			log.error("Error broadcasting MUC presence across groups for user key: {}", userkey, ex);
		}
	}

	/**
	 * Pushes a presence update for a specific user to all occupants of a targeted MUC room.
	 * This method acts as the "Outbound Broadcast" to the group.
	 */
	public void broadcastPresenceToGroupParticipants(ChannelHandlerContext ctx, String userkey, MucRoomDto group, UserState newState) {    
		try {
			String roomJid = jidUtil.getGroupBareJid(group.getId());

			// 2. Identify the sender's membership details for specific room metadata (nickname/role)
			Optional<MucMember> senderMucMember = group.getMembers().stream()
					.filter(m -> m.getUserKey().equals(userkey))
					.findFirst();

			if (senderMucMember.isPresent()) {
				// 3. Construct the XEP-0045 MUC presence stanza
				String presenceXml = MucUserPresenceBuilder
						.create()
						.from(roomJid, senderMucMember.get().getUserKey()) 
						.show(newState.name().toString().toLowerCase())
						.affiliation(senderMucMember.get().getRole())
						.role(MucRoleUtil.getMucRole(senderMucMember.get().getRole()).getValue())
						.build();

				// 4. Distribute via router to all active occupants in the room
				mucMessageRouter.broadcastToOccupants(UUID.randomUUID().toString(), senderMucMember.get().getUserKey(), group, presenceXml, false);
			}
		} catch (Exception ex) {
			log.error("Error broadcasting MUC presence in group {} for user key: {}", group.getId(), userkey, ex);
		}
	}

	public void pushGroupParticipantsPresenceToUser(ChannelHandlerContext ctx, String groupId, XmppPrincipal principal) {  
		pushGroupParticipantsPresenceToUser(ctx, groupId, principal.getUserKey());
	}

	public void pushGroupParticipantsPresenceToUser(ChannelHandlerContext ctx, String groupId, String userKey) {    
		try {
			MucRoomDto group = groupClient.getGroupById(groupId);
			pushGroupParticipantsPresenceToUser(ctx, group, userKey);
		} catch (Exception ex) {
			log.error("Error syncing MUC participant presence for group {}: {}", groupId, ex.getMessage());
		}
	}

	/**
	 * Synchronizes the presence of all current group participants to a newly joined user.
	 * <p>
	 * This performs a "State Dump" by fetching all participant session data from Redis. 
	 * It explicitly handles multi-device arbitration to ensure that if a participant 
	 * has orphan or "zombie" records in Redis, the most relevant state is sent.
	 * </p>
	 */
	public void pushGroupParticipantsPresenceToUser(ChannelHandlerContext ctx, MucRoomDto group, String userKey) { 
		try {	
			if (CollectionUtils.isEmpty(group.getMembers())) {
				return;
			}

			// Validate that the receiving user is a member of the target group
			Optional<MucMember> userMucInfoOpt = group.getMembers().stream()
					.filter(m -> m.getUserKey().equalsIgnoreCase(userKey))
					.findFirst();

			if (userMucInfoOpt.isEmpty()) {
				log.warn("Unauthorized presence sync attempt: user {} does not belong to group {}", userKey, group.getId());
				return;
			}

			String roomJid = jidUtil.getGroupBareJid(group.getId());

			// 2. Collect all keys for batch fetching from Redis (Performance Optimization)
			List<String> memberKeys = group.getMembers().stream()
					.map(MucMember::getUserKey)
					.toList();

			// 3. Batch fetch session/state data for all occupants to avoid N+1 queries
			Map<String, Set<UserSession>> allSessionsMap = userSessionRegistry.getAllSessions(memberKeys);

			String toJid = jidUtil.getBareJid(userKey);
					
			for (MucMember member : group.getMembers()) {
				// Skip the recipient themselves (Standard MUC self-presence is handled separately)
				if (member.getUserKey().equalsIgnoreCase(userKey) ) {
					continue;
				}
				
				Set<UserSession> sessions = allSessionsMap.getOrDefault(member.getUserKey(), Collections.emptySet());

				// Default to GONE if no active sessions are found (indicating a lack of presence)
				UserState newState = UserState.GONE;
				long updatedAt = 0L;

				// 4. Arbitrate between multiple sessions to filter out orphan/zombie data
				if (!sessions.isEmpty()) {
					newState = UserStateUtil.determineOverallState(sessions);
					updatedAt = sessions.stream()
							.mapToLong(UserSession::getUpdatedAt)
							.max()
							.orElse(0L);
				}

				// 5. Build a Group-Specific Presence Stanza (XEP-0045) with Delay support (XEP-0203)
				String presenceXml = MucUserPresenceBuilder
						.create()
						.from(roomJid, member.getUserKey()) 
						.to(toJid)      
						.status(newState.name().toString().toLowerCase())
						.updatedAt(updatedAt != 0 ? Instant.ofEpochMilli(updatedAt).toString() : null)
						.affiliation(member.getRole())
						.role(MucRoleUtil.getMucRole(member.getRole()).getValue())
						.build();

				// Write directly to the Netty outbound pipeline for the receiving client
				localStanzaDispatcher.dispatchLocally(userKey, userKey, presenceXml);
			}		            
		} catch (Exception ex) {
			log.error("Failed to sync MUC participant presence for user key: {}", userKey, ex);
		}
	}
}