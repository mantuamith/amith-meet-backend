package com.algomeet.xmpp.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.document.PinMucMessage;
import com.algomeet.xmpp.chatservice.exceptions.PinMessageNotFoundException;
import com.algomeet.xmpp.chatservice.repository.PinMucMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class PinMucMessageService {
    private final PinMucMessageRepository pinMucMessageRepository;

    // Dedicated thread pool to offload blocking/heavy database processing off WebFlux Netty threads
    private static final Scheduler MUC_DB_SCHEDULER = Schedulers.newBoundedElastic(200, 10000, "xmpp-muc-pin-workers");

    /**
     * Pins a new message inside a specific MUC room context.
     */
    public Mono<PinMucMessage> pinMessage(PinMucMessage pinMucMessage) {
        return pinMucMessageRepository.save(pinMucMessage)
                .subscribeOn(MUC_DB_SCHEDULER)
                .doOnSuccess(saved -> log.debug("Successfully pinned MUC message {} in group {}", 
                        saved.getId().getMessageId(), saved.getId().getGroupId()))
                .doOnError(err -> log.error("Failed to pin MUC message due to database constraint", err));
    }

    /**
     * Unpins a message from a MUC room.
     * Evaluates personal pin deletion ownership first, then falls back to attempting a global room pin removal.
     */
    public Mono<Void> unpinMessage( UUID userKey, UUID groupId, UUID messageId) {
        // 1. Attempt to delete the requesting user's personal pin instance first
        return pinMucMessageRepository.deleteById_GroupIdAndId_MessageIdAndId_PinnedBy(groupId, messageId, userKey)
                .subscribeOn(MUC_DB_SCHEDULER)
                .flatMap(personalDeletedCount -> {
                    if (personalDeletedCount > 0) {
                        log.debug("Successfully unpinned personal message {} from MUC room {}", messageId, groupId);
                        return Mono.<Void>empty(); 
                    }
                    
                    // 2. Fallback: If no personal pin matches, run the global channel cleanup query
                    return pinMucMessageRepository
                            .deleteById_GroupIdAndId_MessageIdAndPinnedForEveryoneIsTrue(groupId, messageId)
                            .flatMap(globalDeletedCount -> {
                                if (globalDeletedCount == 0) {
                                    return Mono.<Void>error(new PinMessageNotFoundException("Pinned MUC message not found."));
                                }
                                log.debug("Successfully unpinned global message {} from MUC room {}", messageId, groupId);
                                return Mono.<Void>empty();
                            });
                })
                .doOnError(err -> log.error("Failed to remove MUC pin record for message {}", messageId, err))
                .then();
    }

    /**
     * Fetches all active pin definitions assigned within a single specific group channel.
     */
    public Flux<PinMucMessage> getPinnedMessagesForGroup(String groupId) {
        return pinMucMessageRepository.findById_GroupId(groupId)
                .subscribeOn(MUC_DB_SCHEDULER)
                .publishOn(MUC_DB_SCHEDULER) // Structural separation boundary for stream mappings
                .doOnError(err -> log.error("Failed to stream pins for MUC room: {}", groupId, err));
    }

    /**
     * Finds pinned messages matching your exact compound index structure inside a MUC space, ordered by seq ascending.
     * Evaluates Visibility boundary rules: matches either targeted user's personal pins OR global channel announcements.
     */
    public Flux<PinMucMessage> findPinnedMessages(String groupId, UUID pinnedBy) {    	
        return pinMucMessageRepository.findPinnedMessages(groupId, pinnedBy)
                .subscribeOn(MUC_DB_SCHEDULER)
                .publishOn(MUC_DB_SCHEDULER)
                .doOnError(err -> log.error("Error matching indexed pin search framework for user {} in MUC room {}", 
                        pinnedBy, groupId, err));
    }
}