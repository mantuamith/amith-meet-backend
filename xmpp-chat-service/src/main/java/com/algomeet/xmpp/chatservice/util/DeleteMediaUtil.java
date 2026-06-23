package com.algomeet.xmpp.chatservice.util;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.xmpp.chatservice.publisher.DeleteMessageMediaEventPublisher;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteMediaUtil {
    private final MucMessageRepository mucMessageRepository;
    private final DeleteMessageMediaEventPublisher deleteMessageMediaEventPublisher;

 // Immutable state holder to pass safely down the reactive recursive expand tree
    private record PaginationState(UUID cursorId, boolean isInclusive) {}
    
    public Mono<Void> handleDeletionOfUserMediaFilesReactive(UUID userKey, Optional<GroupMember> memberPrevData, UUID cutoffStanzaId, UUID groupId) {        
    	Instant prevHistoryCutoff = memberPrevData
    			.map(GroupMember::getMessageHistoryCutoff)
    			.map(Instant::ofEpochMilli)
    			.orElse(Instant.EPOCH);

    	return handleDeletionOfUserMediaFilesReactive(userKey, prevHistoryCutoff, cutoffStanzaId, groupId);
    }

    public Mono<Void> handleDeletionOfUserMediaFilesReactive(UUID userKey, Instant prevHistoryCutoff, UUID cutoffStanzaId, UUID groupId) {
        UUID initialMaxId = cutoffStanzaId;
        final int pageSize = 200;
        Pageable pageable = PageRequest.of(0, pageSize); 

        // Start with an inclusive wrapper state (<=) for the first database lookup pass
        PaginationState initialState = new PaginationState(initialMaxId, true);

        return Mono.just(initialState)
                .expand(state -> {
                    // 1. Dynamically route the query based on the immutable state footprint
                    var queryFlux = state.isInclusive() 
                        ? mucMessageRepository.findMessageViewByRoomIdAndIdLessThanEqualAndToIsNullOrEqualtoUserkeyAndNotHiddenAndMediaIdsIsNotNullOrderByIdDesc(
                                groupId, state.cursorId(), userKey, prevHistoryCutoff, pageable)
                        : mucMessageRepository.findMessageViewByRoomIdAndIdLessThanAndToIsNullOrEqualtoUserkeyAndNotHiddenAndMediaIdsIsNotNullOrderByIdDesc(
                                groupId, state.cursorId(), userKey, prevHistoryCutoff, pageable);

                    return queryFlux
                        .collectList()
                        .flatMapMany(batch -> {
                            if (batch.isEmpty()) {
                                return Flux.empty();
                            }

                            // 2. Process media deletion broker notifications concurrently
                            Flux<Object> publishEvents = Flux.fromIterable(batch)
                                    .filter(msg -> !CollectionUtils.isEmpty(msg.getMediaIds()))
                                    .flatMap(msg -> {
                                        Set<String> mediaIdStrings = msg.getMediaIds().stream()
                                                .map(UUID::toString)
                                                .collect(Collectors.toSet());
                                        
                                        return deleteMessageMediaEventPublisher.publish(
                                                userKey.toString(), 
                                                mediaIdStrings, 
                                                Set.of(userKey.toString()),
                                                null, 
                                                msg.getMessageId().toString()
                                        );
                                    });

                            // 3. Determine the cursor for the next recursive window loop pass
                            UUID lowestIdInBatch = batch.get(batch.size() - 1).getId();
                            
                            // If the batch is full, emit a new strict exclusive state (<) for the next pass
                            Flux<PaginationState> nextState = (batch.size() == pageSize) 
                                    ? Flux.just(new PaginationState(lowestIdInBatch, false)) 
                                    : Flux.empty();

                            // 4. Ensure events finish processing BEFORE expanding further
                            return publishEvents.thenMany(nextState);
                        });
                })
                .doOnError(err -> log.error("Error executing background file revocation tracking loops for group: {}", groupId, err))
                .then(); // Return a clean Mono<Void> signaling sequence terminal completion
    }
}