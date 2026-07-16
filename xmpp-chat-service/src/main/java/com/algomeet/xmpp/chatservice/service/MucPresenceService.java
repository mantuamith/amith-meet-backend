package com.algomeet.xmpp.chatservice.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.Group;
import com.algomeet.common.dto.GroupMember;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.routing.muc.MucMessageRouter;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.presence.MucUserPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.MucRoleUtil;
import com.algomeet.xmpp.chatservice.util.UserStateUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
	private final UserSessionRegistry reactiveUserSessionRegistry;
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
	public Mono<Void> broadcastPresenceToAllJoinedGroups(ChannelHandlerContext ctx, String userkey, UserState newState) {    
		// 1. Fetch all room memberships to identify broadcast targets
		// Wrapped via Mono.fromCallable + boundedElastic to ensure blocking Client I/O doesn't freeze the Netty EventLoop
		return Mono.fromCallable(() -> groupClient.getGroupsForUserKey(userkey))
				.subscribeOn(Schedulers.boundedElastic())
				.flatMapMany(groups -> {
					if (CollectionUtils.isEmpty(groups)) {
						return Flux.empty();
					}
					return Flux.fromIterable(groups);
				})
				.flatMap(group -> broadcastPresenceToGroupParticipants(ctx, userkey, group, newState))
				.then()
				.doOnError(ex -> log.error("Error broadcasting MUC presence across groups for user key: {}", userkey, ex))
				.onErrorResume(ex -> Mono.empty());
	}

	/**
	 * Pushes a presence update for a specific user to all occupants of a targeted MUC room.
	 * This method acts as the "Outbound Broadcast" to the group.
	 */
	public Mono<Void> broadcastPresenceToGroupParticipants(ChannelHandlerContext ctx, String userkey, Group group, UserState newState) {    
		return Mono.defer(() -> {
			String roomJid = jidUtil.getGroupBareJid(group.getId().toString());

			// 2. Identify the sender's membership details for specific room metadata (nickname/role)
			Optional<GroupMember> senderMucMember = group.getMembers().stream()
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
				XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();  
				return mucMessageRouter.broadcastToOccupants(
						UuidCreator.getTimeOrderedEpoch().toString(), 
						senderMucMember.get().getUserKey(), 
						group, 
						presenceXml, 
						principal.getSessionId()
				);
			}
			return Mono.empty();
		})
		.doOnError(ex -> log.error("Error broadcasting MUC presence in group {} for user key: {}", group.getId(), userkey, ex))
		.onErrorResume(ex -> Mono.empty());
	}

	/**
	 * Synchronizes the presence of all current group participants to a newly joined user.
	 * <p>
	 * This performs a "State Dump" by fetching all participant session data from Redis. 
	 * It explicitly handles multi-device arbitration to ensure that if a participant 
	 * has orphan or "zombie" records in Redis, the most relevant state is sent.
	 * </p>
	 */
	public Mono<Void> pushGroupParticipantsPresenceToUser(ChannelHandlerContext ctx, Group group, String userKey) { 
	    try {	
	        if (CollectionUtils.isEmpty(group.getMembers())) {
	            return Mono.empty();
	        }

	        // Validate that the receiving user is a member of the target group
	        Optional<GroupMember> userMucInfoOpt = group.getMembers().stream()
	                .filter(m -> m.getUserKey().equalsIgnoreCase(userKey))
	                .findFirst();

	        if (userMucInfoOpt.isEmpty()) {
	            log.warn("Unauthorized presence sync attempt: user {} does not belong to group {}", userKey, group.getId());
	            return Mono.empty();
	        }

	        String roomBareJid = jidUtil.getGroupBareJid(group.getId().toString());

	        // 2. Collect all keys for batch fetching from Redis (Performance Optimization)
	        List<String> memberKeys = group.getMembers().stream()
	                .map(GroupMember::getUserKey)
	                .toList();

	        // 3. Batch fetch session/state data for all occupants to avoid N+1 queries
	        // Assumes userSessionRegistry has a reactive variant returning Mono<Map<...>>.
	        // If it is strictly blocking, wrap it in Mono.fromCallable(() -> ...).subscribeOn(Schedulers.boundedElastic())
	        Mono<Map<String, Set<UserSession>>> sessionsMono = reactiveUserSessionRegistry.getAllSessions(memberKeys);

	        String toJid = jidUtil.getBareJid(userKey);

	        return sessionsMono.flatMap(allSessionsMap -> {
	            // Transform the static group members list into a reactive stream (Flux)
	            return Flux.fromIterable(group.getMembers())
	                // Skip the recipient themselves (Standard MUC self-presence is handled separately)
	                .filter(member -> !member.getUserKey().equalsIgnoreCase(userKey))
	                .flatMap(member -> {
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
	                            .from(roomBareJid, member.getUserKey()) 
	                            .to(toJid)      
	                            .status(newState.name().toString().toLowerCase())
	                            .updatedAt(updatedAt != 0 ? Instant.ofEpochMilli(updatedAt).toString() : null)
	                            .affiliation(member.getRole())
	                            .role(MucRoleUtil.getMucRole(member.getRole()).getValue())
	                            .build();

	                    // Write directly to the Netty outbound pipeline for the receiving client
	                    // Instead of calling .subscribe() prematurely inside a loop, we return the inner Mono 
	                    // to keep the reactive chain intact and non-blocking.
	                    return localStanzaDispatcher.dispatchLocally(userKey, userKey, presenceXml);
	                })
	                // Concurrently triggers and waits for all local dispatches to complete
	                .then(); 
	        })
	        .doOnError(ex -> log.error("Failed to sync MUC participant presence for user key: {}", userKey, ex))
	        // Gracefully swallows or propagates errors matching your original catch-block strategy
	        .onErrorResume(ex -> Mono.empty()); 

	    } catch (Exception ex) {
	        // Keeps a guard catch just in case synchronous building code prior to the Mono chain fails
	        log.error("Failed to build MUC participant presence chain for user key: {}", userKey, ex);
	        return Mono.empty();
	    }
	}
}