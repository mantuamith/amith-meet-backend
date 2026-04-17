package com.algomeet.xmpp.chatservice.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.ContactClient;
import com.algomeet.xmpp.chatservice.cluster.publisher.ClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;
import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.presence.DirectedPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for synchronizing and distributing contact presence information.
 * * <p>This service handles two main flows:</p>
 * <ul>
 * <li><b>Inbound Synchronization:</b> Fetching the current status of all friends when a user logs in.</li>
 * <li><b>Outbound Broadcast:</b> Pushing a user's own status changes to their contact list across the cluster.</li>
 * </ul>
 * * <p>To maintain high performance in Netty, heavy I/O operations (Redis batching and REST calls) 
 * are offloaded to a dedicated thread pool.</p>
 * * @author Algomeet Core Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactPresenceService {
    private final UserSessionRegistry userSessionRegistry;
    private final ContactClient contactClient;
    private final DomainProperties domainProperties;
    private final JidUtil jidUtil;
    private final ClusterMessagePublisher clusterMessagePublisher;

    /**
     * Internal logic for fetching and writing contact statuses.
     * Uses Redis pipelining via the registry to avoid N+1 query performance degradation.
     */
    public void pushContactsPresenceToUser(ChannelHandlerContext ctx, XmppPrincipal principal) {
        try {
        	// Set tenant Id to support multi-tenancy 
    		TenantContext.setCurrentTenant(principal.getTenantId());
    		
            // 1. Retrieve the list of accepted contacts from the Relationship Service
            List<UUID> acceptedContacts = contactClient.getAcceptedContacts(UUID.fromString(principal.getUserKey()));
            if (CollectionUtils.isEmpty(acceptedContacts)) return;

            List<String> contactKeys = acceptedContacts.stream()
                    .map(UUID::toString)
                    .toList();

            // 2. Batch fetch all session data for these contacts in one Redis round-trip
            Map<String, Set<UserSession>> allSessionsMap = userSessionRegistry.getAllSessions(contactKeys);

            // 3. Iterate through contacts to build and send individual presence stanzas
            for (UUID contactUserKey : acceptedContacts) {
                Set<UserSession> sessions = allSessionsMap.getOrDefault(contactUserKey.toString(), Collections.emptySet());
                
                UserState newState = UserState.GONE;
                long updatedAt = 0L;

                // Determine the most relevant state if the contact has multiple devices online
                if (!sessions.isEmpty()) {
                    newState = determineOverallState(sessions);
                    // Use the latest activity timestamp for the XEP-0203 delay stamp
                    updatedAt = sessions.stream()
                            .mapToLong(UserSession::getUpdatedAt)
                            .max()
                            .orElse(0L);
                }

                // Construct the XMPP <presence/> element
                String presenceXml = new DirectedPresenceBuilder()
                        .from(jidUtil.getBareJid(contactUserKey.toString())) // From the contact
                        .to(principal.getBareJid())                         // To the logged-in user
                        .state(newState)
                        .updatedAt(updatedAt)
                        .domain(domainProperties.getDomain())
                        .build();

                // Directly write to the Netty channel (thread-safe operation)
                ctx.writeAndFlush(new TextWebSocketFrame(presenceXml));
            }
            log.debug("Completed presence push for {}", principal.getUserKey());

        } catch (Exception ex) {
            log.error("Failed to push presence for user {}: {}", principal.getUserKey(), ex.getMessage());
        }
    }

    /**
     * Arbitrates the "Global State" for a user with multiple active sessions.
     * Priority: DND > ACTIVE > INACTIVE > AWAY > GONE.
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
        
    /**
     * Broadcasts a user's own status change to their roster across the cluster.
     * This is the "Outbound" push triggered by a presence update from the client.
     *
     * @param ctx       The Netty context.
     * @param principal The identity of the user changing their status.
     * @param newState  The new state to be broadcasted (e.g., AWAY, DND).
     */
    public void broadcastPresenceToContacts(ChannelHandlerContext ctx, XmppPrincipal principal, UserState newState) {
    	try {
    		// Set tenant Id to support multi-tenancy 
    		TenantContext.setCurrentTenant(principal.getTenantId());
    		
            // Fetch contacts who need to receive this update
			List<UUID> acceptedContacts = contactClient.getAcceptedContacts(UUID.fromString(principal.getUserKey()));

			if (!CollectionUtils.isEmpty(acceptedContacts)) {
				for (UUID contactUserKey : acceptedContacts) {
					// Build the presence stanza specifically for this recipient
					String directPresence = new DirectedPresenceBuilder()
						    .from(principal.getBareJid())
						    .to(jidUtil.getBareJid(contactUserKey.toString()))
						    .state(newState)
						    .build();					

                    // Publish to the cluster so users on other nodes receive the update
					clusterMessagePublisher.convertAndSendToUser(
							UUID.randomUUID().toString(), 
							contactUserKey.toString(), 
							principal.getUserKey(), 
							ChatType.CHAT, 
							directPresence
							);
				}				
			}

		} catch (Exception ex) {
			log.error("Error retrieving contacts for presence update using user key: {}", principal.getUserKey(), ex);
		}
    }
}