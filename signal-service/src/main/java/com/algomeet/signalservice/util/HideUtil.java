package com.algomeet.signalservice.util;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.algomeet.signalservice.document.MessageBackupDocument;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class HideUtil {
    private final MongoTemplate mongoTemplate;

    /**
     * Hides all messages tied to a target message id inside a specific room.
     * Updates deletedAt, clears stanzaXml, and updates the synchronization cursor.
     *
     * @return A Mono emitting the number of modified documents
     */
    public void hideRelatedMessages(UUID userKey, List<UUID> targetMessageIds) {
        if (userKey == null || CollectionUtils.isEmpty(targetMessageIds)) {
            return;
        }

        // 1. Match the exact room and targeted message IDs using the compound index
        Query query = new Query(
            Criteria.where(MessageBackupDocument.FIELD_USER_KEY).is(userKey)
                    .and(MessageBackupDocument.FIELD_TARGET_MESSAGE_ID).in(targetMessageIds)
        );

        // 2. Define updates: Set deletedAt, clear XML, and step the update cursor
        Update update = new Update()
        	.set(MessageBackupDocument.FIELD_HIDDEN_AT, Instant.now().toEpochMilli())
            .set(MessageBackupDocument.FIELD_ENCRYPTED_MSG, null)
            // CRITICAL: Generate a new UUIDv7 sync cursor so offline clients 
            // know these child reactions/edits were modified during catch-up sync!
            .set(MessageBackupDocument.FIELD_UPDATE_CURSOR_ID, UuidCreator.getTimeOrderedEpoch()); 

        // 3. Execute bulk update across all matching documents
        mongoTemplate.updateMulti(query, update, MessageBackupDocument.class);
    }
}