package com.algomeet.xmpp.chatservice.util;

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
public class HidetUtil {

    private final ReactiveMongoTemplate reactiveMongoTemplate;

    /**
     * Hide all messages tied to a target message id inside a specific room for a specific user.
     * Appends the userKey to hiddenFromUserKeys and updates the synchronization cursor.
     *
     * @return A Mono emitting the number of modified documents
     */
    public Mono<Long> hideRelatedMessages(UUID userKey, UUID roomId, UUID targetMessageId) {
        if (userKey == null || roomId == null || targetMessageId == null) {
            return Mono.just(0L);
        }

        // 1. Match the exact room and targeted message IDs using the compound index
        Query query = new Query(
            Criteria.where(MucMessage.FIELD_ROOM_ID).is(roomId)
                    .and("targetMessageId").is(targetMessageId)
        );

        // 2. Define updates: Push to set atomically and step the update cursor
        Update update = new Update()
            // Using addToSet ensures userKey is appended to the array uniquely
            .addToSet("hiddenFromUserKeys", userKey)

            // CRITICAL: Generate a new UUIDv7 sync cursor so offline clients 
            // know these child reactions/edits were modified during catch-up sync!
            .set("updateCursorId", UuidCreator.getTimeOrderedEpoch()); 

        // 3. Execute bulk update across all matching documents
        return reactiveMongoTemplate.updateMulti(query, update, MucMessage.class)
            .map(updateResult -> updateResult.getModifiedCount());
    }
}