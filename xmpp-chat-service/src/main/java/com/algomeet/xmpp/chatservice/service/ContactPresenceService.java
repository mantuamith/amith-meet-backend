package com.algomeet.xmpp.chatservice.service;

import java.util.List;
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
import com.github.f4b6a3.uuid.UuidCreator;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Service responsible for synchronizing and distributing contact presence information.
 * <p>This service manages the lifecycle of distributed user states stored in Redis, 
 * ensuring that "zombie" or orphan session data is arbitrated correctly during presence pushes.</p>
 * <p>Two main distribution flows are handled:</p>
 * <ul>
 * <li><b>Inbound Synchronization:</b> Initial fetch of friends' status from Redis upon login, 
 * resolving conflicts if multiple session records exist for a single contact.</li>
 * <li><b>Outbound Broadcast:</b> Real-time distribution of a user's presence (Active, Inactive, etc.) 
 * to their roster across the cluster nodes.</li>
 * </ul>
 * @author Algomeet Core Team
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
	 * @param ctx       The Netty ChannelHandlerContext for direct WebSocket writes.
	 * @param principal The principal of the user receiving the presence update.
	 * @return 
	 */
	public Mono<Void> pushContactsPresenceToUser(ChannelHandlerContext ctx, XmppPrincipal principal) {
	    // 1. Wrap the blocking HTTP call safely onto the elastic scheduler while maintaining multi-tenant context
	    return fetchAcceptedContacts(principal.getTenantId(), UUID.fromString(principal.getUserKey()))
	        .filter(contacts -> !CollectionUtils.isEmpty(contacts))
	        .flatMap(acceptedContacts -> {
	            
	            // 2. Extract batch keys for the non-blocking Redis call
	            List<String> contactKeys = acceptedContacts.stream().map(UUID::toString).toList();
	            
	            // 3. Reactively fetch and flatMap the session map
	            return userSessionRegistry.getAllSessions(contactKeys)
	                .flatMap(allSessionsMap -> {
	                    
	                    // 4. Process dispatches concurrently
	                    return Flux.fromIterable(acceptedContacts)
	                        .flatMap(contactUserKey -> {
	                            Set<UserSession> sessions = allSessionsMap.getOrDefault(contactUserKey.toString(), java.util.Collections.emptySet());
	                            UserState newState = sessions.isEmpty() ? UserState.GONE : UserStateUtil.determineOverallState(sessions);
	                            long updatedAt = sessions.stream().mapToLong(UserSession::getUpdatedAt).max().orElse(0L);

	                            String presenceXml = new DirectedPresenceBuilder()
	                                    .from(jidUtil.getBareJid(contactUserKey.toString()))                     
	                                    .state(newState)
	                                    .updatedAt(updatedAt)
	                                    .domain(domainProperties.getDomain())
	                                    .build();

	                            return localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), presenceXml);
	                        }, 16) // Max concurrency factor
	                        .then(); // Emits completion signal for this collection batch
	                });
	        });       
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
	 * @return 
	 */
	public Mono<Void> broadcastPresenceToContacts(ChannelHandlerContext ctx, XmppPrincipal principal, UserState newState) {
		// FIX: Completely eliminate ThreadLocal context leaks by treating the relationship lookup as a safe, isolated reactive step
		return fetchAcceptedContacts(principal.getTenantId(), UUID.fromString(principal.getUserKey()))
			.filter(contacts -> !CollectionUtils.isEmpty(contacts))
			.flatMap(acceptedContacts -> {
				
				// Construct the broadcast presence stanza once rather than repetitively inside a heavy loop
				String directPresence = new DirectedPresenceBuilder()
						.from(principal.getBareJid()) 
						.state(newState)
						.build();

				// FIX: Convert the iterative dispatch into a backpressure-aware reactive stream without blocking threads or orphaned subscriptions
				return Flux.fromIterable(acceptedContacts)
						.flatMap(contactUserKey -> clusterMessagePublisher.convertAndSendToUser(
								UuidCreator.getTimeOrderedEpoch().toString(), 
								contactUserKey.toString(), 
								principal.getUserKey(), 
								ChatType.CHAT, 
								directPresence
						), 32) // Maintain strict concurrency boundaries on the cluster publisher
						.then();
			});		
	}

	/**
	 * Helper method to safely offload the blocking contact client HTTP handshake onto the elastic scheduler 
	 * while ensuring TenantContext ThreadLocal variables are explicitly set and cleaned up on the target worker thread.
	 */
	private Mono<List<UUID>> fetchAcceptedContacts(Integer tenantId, UUID userKey) {
		return Mono.fromCallable(() -> {
			try {
				TenantContext.setCurrentTenant(tenantId);
				return contactClient.getAcceptedContacts(userKey);
			} finally {
				TenantContext.clear();
			}
		}).subscribeOn(Schedulers.boundedElastic());
	}
}