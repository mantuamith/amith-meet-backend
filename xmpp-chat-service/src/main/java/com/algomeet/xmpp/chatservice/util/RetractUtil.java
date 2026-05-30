package com.algomeet.xmpp.chatservice.util;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RetractUtil {

    private final ReactiveMongoTemplate reactiveMongoTemplate;

    /**
     * Retracts all messages tied to a target message id inside a specific room.
     * Updates deletedAt, clears stanzaXml, and updates the synchronization cursor.
     *
     * @return A Mono emitting the number of modified documents
     */
    public Mono<Long> retractRelatedMessages(UUID roomId, UUID targetMessageId) {
        if (roomId == null || targetMessageId == null) {
            return Mono.just(0L);
        }

        // 1. Match the exact room and targeted message IDs using the compound index
        Query query = new Query(
            Criteria.where(MucMessage.FIELD_ROOM_ID).is(roomId)
                    .and("targetMessageId").is(targetMessageId)
        );

        // 2. Define updates: Set deletedAt, clear XML, and step the update cursor
        Update update = new Update()
            .set("deletedAt", Instant.now().toEpochMilli())
            .set("stanzaXml", null)
            // CRITICAL: Generate a new UUIDv7 sync cursor so offline clients 
            // know these child reactions/edits were modified during catch-up sync!
            .set("updateCursorId", UuidCreator.getTimeOrderedEpoch()); 

        // 3. Execute bulk update across all matching documents
        return reactiveMongoTemplate.updateMulti(query, update, MucMessage.class)
            .map(updateResult -> updateResult.getModifiedCount());
    }
}