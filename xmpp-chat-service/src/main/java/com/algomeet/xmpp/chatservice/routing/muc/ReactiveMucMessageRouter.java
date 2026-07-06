package com.algomeet.xmpp.chatservice.routing.muc;

import org.springframework.stereotype.Component;

import com.algomeet.common.dto.Group;
import com.algomeet.common.dto.GroupMember;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.cluster.publisher.ReactiveClusterMessagePublisher;
import com.algomeet.xmpp.chatservice.enums.ChatType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReactiveMucMessageRouter {
	private final ReactiveClusterMessagePublisher reactiveClusterMessagePublisher;
	
	/**
	 * Iterates through all room occupants and publishes the presence update to the cluster 
	 * except don't echo to same user session ID as sender.
	 */
	public Mono<Void> broadcastToOccupants(String id, String senderKey, Group group, String payload, String sessionId) {
		// Guard clause to handle missing groups or empty member structures gracefully
		if (group == null || group.getMembers() == null || group.getMembers().isEmpty()) {
			log.warn("Attempted to broadcast MUC message to an empty or non-existent group. StanzaId: {}", id);
			return Mono.empty();
		}
		
		XmppPrincipal principal = XmppPrincipal.builder()
				.sessionId(sessionId)
				.build();
		
		// Convert the list of members into a reactive stream and process concurrently via flatMap
		return Flux.fromIterable(group.getMembers())
				.flatMap(receiver -> {
					log.debug("Routing MUC stanza [{}] from [{}] to subscriber [{}] via cluster bus.", 
							id, senderKey, receiver.getUserKey());
					
					return reactiveClusterMessagePublisher.convertAndSendToUser(
							id, 
							receiver.getUserKey(), 
							senderKey, 
							ChatType.GROUPCHAT, 
							false, // isAllowEcho = false ensures duplicate suppression via session ID matches
							payload, 
							principal
					);
				})
				.then(); // Aggregate downstream signaling back into a single Mono<Void> completion token
	}
}