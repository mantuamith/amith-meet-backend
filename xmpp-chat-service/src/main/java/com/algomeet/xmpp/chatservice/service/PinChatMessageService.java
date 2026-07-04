package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.common.util.DeterministicConversationIdUtil;
import com.algomeet.xmpp.chatservice.document.PinChatMessage;
import com.algomeet.xmpp.chatservice.exceptions.PinMessageNotFoundException;
import com.algomeet.xmpp.chatservice.repository.PinChatMessageRepository;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class PinChatMessageService {

    private final PinChatMessageRepository pinChatMessageRepository;

    // Dedicated pool to offload reactive tracking metrics, mappings, and pipeline setups off Netty threads
    private static final Scheduler DB_SCHEDULER = Schedulers.newBoundedElastic(200, 10000, "xmpp-pin-db-workers");

    /**
     * Pins a new message inside a conversation context.
     */
    public Mono<PinChatMessage> pinMessage(PinChatMessage pinChatMessage) {  	
    	
        return pinChatMessageRepository.save(pinChatMessage)
                .subscribeOn(DB_SCHEDULER)
                .doOnSuccess(saved -> log.debug("Successfully pinned message {} in conversation {}", 
                        saved.getId().getMessageId(), saved.getId().getConversationId()))
                .doOnError(err -> log.error("Failed to pin message due to database constraint", err));
    }

    /**
     * Unpins a message by its conversation and specific unique payload message ID.
     */
    /**
     * Unpins a message by its conversation and specific unique payload message ID.
     * Evaluates ownership first (deleting personal pin) and falls back to clearing a global pin
     * if the requesting user didn't pin it personally.
     */
    public Mono<Void> unpinMessage(UUID userKey, UUID peerKey, UUID messageId) {
        String conversationId = DeterministicConversationIdUtil.getConversationId(userKey, peerKey);

        // 1. Attempt to delete the personal pin first
        return pinChatMessageRepository.deleteById_ConversationIdAndId_MessageIdAndId_PinnedBy(conversationId, messageId, userKey)
                .subscribeOn(DB_SCHEDULER)
                .flatMap(personalDeletedCount -> {
                    // If a personal pin was matched and deleted, exit the chain early
                    if (personalDeletedCount > 0) {
                        log.debug("Successfully unpinned personal message {} from conversation {}", messageId, conversationId);
                        return Mono.<Void>empty(); 
                    }
                    
                    // 2. Fallback: If 0 personal pins were deleted, run the global deletion query
                    return pinChatMessageRepository
                            .deleteById_ConversationIdAndId_MessageIdAndPinnedForEveryoneIsTrue(conversationId, messageId)
                            .flatMap(globalDeletedCount -> {
                                if (globalDeletedCount == 0) {
                                    return Mono.<Void>error(new PinMessageNotFoundException("Pinned message not found."));
                                }
                                log.debug("Successfully unpinned global message {} from conversation {}", messageId, conversationId);
                                return Mono.<Void>empty();
                            });
                })
                .doOnError(err -> log.error("Failed to remove pin record for message {}", messageId, err))
                .then(); // Guarantees type-safety return of Mono<Void>
    }

    /**
     * Fetches all active pin definitions matching a specific chat window sequence scope.
     */
    public Flux<PinChatMessage> getPinnedMessagesForConversation(UUID peerKey) {
    	UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
    	String conversationId = DeterministicConversationIdUtil.getConversationId(userKey, peerKey);
    	
        return pinChatMessageRepository.findById_ConversationId(conversationId)
                .subscribeOn(DB_SCHEDULER)
                .publishOn(DB_SCHEDULER) // Safeguard context-switches if mapped sequentially downstream
                .doOnError(err -> log.error("Failed to stream pins for conversation: {}", conversationId, err));
    }

	/**
     * Finds pinned messages matching your exact compound index structure, ordered by seq ascending.
     * Matches: conversationId AND (pinnedBy OR pinnedForEveryone == true)
     * Sorts: { 'seq': 1 } (1 = Ascending, -1 = Descending)
     */
    public Flux<PinChatMessage> findPinnedMessages(UUID userKey, UUID peerKey, UUID pinnedBy) {    	
    	String conversationId = DeterministicConversationIdUtil.getConversationId(userKey, peerKey);
    	
        return pinChatMessageRepository.findPinnedMessages(conversationId, pinnedBy)
                .subscribeOn(DB_SCHEDULER)
                .publishOn(DB_SCHEDULER) // Enforces that downstream stream handlers run safely on the DB pool
                .doOnError(err -> log.error("Error matching indexed pin search framework for user {} in room {}", 
                        pinnedBy, conversationId, err));
    }
}