package com.algomeet.xmpp.chatservice.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.ContactClient;
import com.algomeet.xmpp.chatservice.client.GroupClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.routing.muc.events.MucMemberOrdinaryPresenceEventHandler;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.presence.DirectedPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;

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
    private final MucMemberOrdinaryPresenceEventHandler mucMemberPresenceEventHandler;

    /**
     * Broadcasts the current user's status change to all participants in every MUC 
     * room they are currently joined in.
     * * @param ctx       The Netty context for the active connection.
     * @param principal The authenticated user identity.
     * @param newState  The new state to broadcast (ACTIVE, AWAY, etc.).
     */
    public void broadcastPresenceToParticipants(ChannelHandlerContext ctx, XmppPrincipal principal, UserState newState) {    
        try {
            // 1. Fetch all rooms the user is currently a member of
            List<MucRoomDto> groups = groupClient.getGroupsForUserKey(principal.getUserKey());

            if (!CollectionUtils.isEmpty(groups)) {
                for (MucRoomDto group : groups) {
                    String roomJid = jidUtil.getGroupBareJid(group.getId());

                    // 2. Identify the sender's membership details for specific room metadata (nickname)
                    Optional<MucMember> senderMucMember = group.getMembers().stream()
                            .filter(m -> m.getUserKey().equals(principal.getUserKey()))
                            .findFirst();

                    if (senderMucMember.isPresent()) {
                        // 3. Delegate to event handler to push XML stanzas to all room occupants
                        mucMemberPresenceEventHandler.handleMemberPresence(
                                ctx, 
                                roomJid, 
                                null, // resource is null as we are broadcasting global state
                                group, 
                                senderMucMember.get(),
                                newState
                                );
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Error broadcasting MUC presence for user key: {}", principal.getUserKey(), ex);
        }
    }
    
    /**
     * Syncs the presence of all occupants in a user's joined rooms back to the user.
     * * <p>This is typically called upon login or upon joining a room to populate the 
     * occupant list. It uses Redis pipelining to batch-fetch statuses for performance.</p>
     * * @param ctx       The Netty context to send stanzas to.
     * @param principal The user receiving the roster status updates.
     */
    public void pushGroupParticipantsPresenceToUser(ChannelHandlerContext ctx, XmppPrincipal principal) {    
        try {
            // 1. Fetch all groups the user belongs to
            List<MucRoomDto> groups = groupClient.getGroupsForUserKey(principal.getUserKey());

            if (CollectionUtils.isEmpty(groups)) {
                return;
            }

            for (MucRoomDto group : groups) {
                String roomJid = jidUtil.getGroupBareJid(group.getId());

                if (CollectionUtils.isEmpty(group.getMembers())) {
                    continue;
                }

                // 2. Collect all member keys for batch processing
                List<String> memberKeys = group.getMembers().stream()
                        .map(MucMember::getUserKey)
                        .toList();

                // 3. Perform a single batch-fetch from Redis for all members in this room
                Map<String, Set<UserSession>> allSessionsMap = userSessionRegistry.getAllSessions(memberKeys);

                for (String memberKey : memberKeys) {
                    Set<UserSession> sessions = allSessionsMap.getOrDefault(memberKey, Collections.emptySet());
                    
                    UserState newState = UserState.GONE;
                    long updatedAt = 0L;

                    // 4. Multi-device arbitration for the room occupant
                    if (!sessions.isEmpty()) {
                        newState = determineOverallState(sessions);
                        updatedAt = sessions.stream()
                                .mapToLong(UserSession::getUpdatedAt)
                                .max()
                                .orElse(0L);
                    }

                    // 5. Build a Group-Specific Presence Stanza (XEP-0045)
                    String presenceXml = new DirectedPresenceBuilder()
                            .asGroup()
                            .from(roomJid + "/" + memberKey) // Resource-part is the member's room identity
                            .to(principal.getBareJid())      // Delivered to the newly connected user
                            .state(newState)
                            .updatedAt(updatedAt)
                            .domain(domainProperties.getDomain())
                            .build();

                    // Send to the Netty outbound pipeline
                    ctx.writeAndFlush(new TextWebSocketFrame(presenceXml));
                }		            
            }
        } catch (Exception ex) {
            log.error("Error syncing MUC participant presence for user key: {}", principal.getUserKey(), ex);
        }
    }
    
    /**
     * Arbitrates the "Global State" for a user with multiple active sessions.
     * Priority: DND > ACTIVE > AWAY > INACTIVE > GONE.
     * * @param sessions A set of all concurrent sessions for a single user.
     * @return The highest priority UserState.
     */
    private UserState determineOverallState(Set<UserSession> sessions) {
        if (sessions.stream().anyMatch(s -> s.getState() == UserState.DND)) return UserState.DND;
        if (sessions.stream().anyMatch(s -> s.getState() == UserState.ACTIVE)) return UserState.ACTIVE;
        if (sessions.stream().anyMatch(s -> s.getState() == UserState.AWAY)) return UserState.AWAY;
        if (sessions.stream().anyMatch(s -> s.getState() == UserState.INACTIVE)) return UserState.INACTIVE;
        return UserState.GONE;
    }
}