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
import com.algomeet.xmpp.chatservice.publisher.MessageMediaDeleteEventPublisher;
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
    private final MessageMediaDeleteEventPublisher messageMediaDeleteEventPublisher;

    public Mono<Void> handleDeletionOfMediaFilesReactive(UUID userKey, Optional<GroupMember> memberPrevData, String cutoffStanzaId, UUID groupId) {
        
        Instant prevHistoryCutoff = memberPrevData
                .map(GroupMember::getMessageHistoryCutoff)
                .map(Instant::ofEpochMilli)
                .orElse(Instant.EPOCH);

        UUID initialMaxId = UUID.fromString(cutoffStanzaId);
        // Fixed page point at 0 since we shift windows via Keyset Pagination
        Pageable pageable = PageRequest.of(0, 500); 

        return Mono.just(initialMaxId)
                .expand(currentMaxId -> 
                    mucMessageRepository.findMessageViewByRoomIdAndIdLessThanEqualAndToIsNullOrEqualtoUserkeyAndNotHiddenAndMediaIdsIsNotNullOrderByIdDesc(
                            groupId, currentMaxId, userKey, prevHistoryCutoff, pageable)
                    .collectList()
                    .flatMapMany(batch -> {
                        if (batch.isEmpty()) {
                            return Flux.empty();
                        }

                        // 1. Process items in the current batch reactively
                        Flux<Object> publishEvents = Flux.fromIterable(batch)
                                .filter(msg -> !CollectionUtils.isEmpty(msg.getMediaIds()))
                                .flatMap(msg -> {
                                    Set<String> mediaIdStrings = msg.getMediaIds().stream()
                                            .map(UUID::toString)
                                            .collect(Collectors.toSet());
                                    
                                    return messageMediaDeleteEventPublisher.publish(
                                            userKey.toString(), 
                                            mediaIdStrings, 
                                            Set.of(userKey.toString()),
                                            null, 
                                            msg.getMessageId().toString()
                                    );
                                });

                        // 2. Determine the cursor for the next window
                        UUID lowestIdInBatch = batch.get(batch.size() - 1).getId();
                        Flux<UUID> nextCursor = (batch.size() == pageable.getPageSize()) 
                                ? Flux.just(lowestIdInBatch) 
                                : Flux.empty();

                        // 3. Complete processing of current events BEFORE emitting the next cursor
                        // This fixes the Type Mismatch completely.
                        return publishEvents.thenMany(nextCursor);
                    })
                )
                .doOnError(err -> log.error("Error executing background file revocation tracking loops for group: {}", groupId, err))
                .then();
    }
}