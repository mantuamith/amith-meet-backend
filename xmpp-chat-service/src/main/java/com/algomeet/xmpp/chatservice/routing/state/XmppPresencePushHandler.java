package com.algomeet.xmpp.chatservice.routing.state;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.service.ContactPresenceService;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Handler responsible for pushing the initial "world state" of presence to a user.
 * * <p>When a user first connects or authenticates, they need to know the availability 
 * of everyone in their ecosystem (Direct Contacts and MUC Room participants). 
 * This class orchestrates those separate data streams into a single logical push.</p>
 * * @author Algomeet Core Team
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class XmppPresencePushHandler {
    private final ContactPresenceService contactPresenceService;

    /**
     * Executes a full presence synchronization asynchronously upon user connection.
     * * <p>This method is triggered immediately after successful authentication. Because 
     * fetching rosters and querying Redis for hundreds of contacts is I/O intensive, 
     * this is offloaded to the {@code pushPresenceExecutor} to ensure the Netty 
     * EventLoop remains responsive to other users.</p>
     *
     * @param ctx       The Netty {@link ChannelHandlerContext} used to write the 
     * resulting XML stanzas to the socket.
     * @param principal The authenticated identity of the user receiving the updates.
     * @return 
     */
    public Mono<Void> pushUsersPresence(ChannelHandlerContext ctx, XmppPrincipal principal) { 
        log.info("Initiating full presence sync for user: {}", principal.getUserKey());

        // 1. Sync 1:1 Contact Presence (Roster)
        // Fetches statuses of all users in the user's accepted contact list.
        return contactPresenceService.pushContactsPresenceToUser(ctx, principal);        
    }	
}