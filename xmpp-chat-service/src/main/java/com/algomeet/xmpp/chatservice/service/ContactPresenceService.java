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
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.session.UserSessionRegistry;
import com.algomeet.xmpp.chatservice.session.model.UserSession;
import com.algomeet.xmpp.chatservice.stanza.presence.DirectedPresenceBuilder;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.UserStateUtil;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for synchronizing and distributing contact presence information.
 * <p>This service manages the lifecycle of distributed user states stored in Redis, 
 * ensuring that "zombie" or orphan session data is arbitrated correctly during presence pushes.</p>
 * * <p>Two main distribution flows are handled:</p>
 * <ul>
 * <li><b>Inbound Synchronization:</b> Initial fetch of friends' status from Redis upon login, 
 * resolving conflicts if multiple session records exist for a single contact.</li>
 * <li><b>Outbound Broadcast:</b> Real-time distribution of a user's presence (Active, Inactive, etc.) 
 * to their roster across the cluster nodes.</li>
 * </ul>
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
	private final LocalStanzaDispatcher localStanzaDispatcher;

	/**
	 * Synchronizes and pushes current contact statuses to a newly connected user.
	 * <p>
	 * This method utilizes Redis batching to retrieve the current state of all accepted contacts. 
	 * It acts as a primary filter for "zombie" data by using {@link #determineOverallState} 
	 * to arbitrate between multiple session records (e.g., if an orphan session wasn't properly evicted).
	 * </p>
	 * * @param ctx       The Netty ChannelHandlerContext for direct WebSocket writes.
	 * @param principal The principal of the user receiving the presence update.
	 */
	public void pushContactsPresenceToUser(ChannelHandlerContext ctx, XmppPrincipal principal) {
		try {
			// Set tenant Id for multi-tenant data isolation
			TenantContext.setCurrentTenant(principal.getTenantId());

			// 1. Retrieve the roster from the external Relationship Service
			List<UUID> acceptedContacts = contactClient.getAcceptedContacts(UUID.fromString(principal.getUserKey()));
			if (CollectionUtils.isEmpty(acceptedContacts)) return;

			List<String> contactKeys = acceptedContacts.stream()
					.map(UUID::toString)
					.toList();

			// 2. Batch fetch all Redis session/presence records to minimize round-trips
			Map<String, Set<UserSession>> allSessionsMap = userSessionRegistry.getAllSessions(contactKeys);

			// 3. Construct presence stanzas based on current distributed state
			for (UUID contactUserKey : acceptedContacts) {
				Set<UserSession> sessions = allSessionsMap.getOrDefault(contactUserKey.toString(), Collections.emptySet());

				// Default to GONE if no valid sessions or only orphan records exist
				UserState newState = UserState.GONE;
				long updatedAt = 0L;

				if (!sessions.isEmpty()) {
					// Arbitrate state to ensure zombie records don't override live "Active" sessions
					newState = UserStateUtil.determineOverallState(sessions);
					// Use latest activity for XEP-0203 delay stamp synchronization
					updatedAt = sessions.stream()
							.mapToLong(UserSession::getUpdatedAt)
							.max()
							.orElse(0L);
				}

				// Build directed presence stanza (from contact -> to receiving user)
				String presenceXml = new DirectedPresenceBuilder()
						.from(jidUtil.getBareJid(contactUserKey.toString())) 
						.to(principal.getBareJid())                         
						.state(newState)
						.updatedAt(updatedAt)
						.domain(domainProperties.getDomain())
						.build();

				// Direct write to the local Netty pipeline
				localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), presenceXml);
			}
			log.debug("Successfully pushed presence roster for user {}", principal.getUserKey());

		} catch (Exception ex) {
			log.error("Presence push failed for user {}: {}", principal.getUserKey(), ex.getMessage());
		}
	}

	/**
	 * Distributes a user's presence update to their entire contact list via the cluster.
	 * <p>
	 * When a user's state changes (e.g., via manual update or connection cleanup), 
	 * this method broadcasts the new state to all nodes to ensure cluster-wide visibility.
	 * </p>
	 *
	 * @param ctx       The Netty context.
	 * @param principal The user initiating the status change.
	 * @param newState  The new presence state to broadcast.
	 */
	public void broadcastPresenceToContacts(ChannelHandlerContext ctx, XmppPrincipal principal, UserState newState) {
		try {
			TenantContext.setCurrentTenant(principal.getTenantId());

			List<UUID> acceptedContacts = contactClient.getAcceptedContacts(UUID.fromString(principal.getUserKey()));

			if (!CollectionUtils.isEmpty(acceptedContacts)) {
				for (UUID contactUserKey : acceptedContacts) {
					// Construct the broadcast presence stanza
					String directPresence = new DirectedPresenceBuilder()
							.from(principal.getBareJid()) // Removed redundant "BROADCAST" string for standard JID compliance
							.to(jidUtil.getBareJid(contactUserKey.toString()))
							.state(newState)
							.build();					

					// Distribute via Cluster Message Publisher to handle users on different server nodes
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
			log.error("Failed to broadcast presence update for user {}: {}", principal.getUserKey(), ex.getMessage());
		}
	}
}