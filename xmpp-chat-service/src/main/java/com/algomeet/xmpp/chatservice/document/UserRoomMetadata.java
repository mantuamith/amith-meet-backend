package com.algomeet.xmpp.chatservice.document;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Data;

/**
 * User Progress Tracking (The "Checkmark" Logic)
 * Tracks the furthest message a user has interacted with in a room.
 */
@Data
@Builder
@Document(collection = "user_room_metadata")
@CompoundIndex(name = "user_room_unique", def = "{'userKey': 1, 'roomId': 1}", unique = true)
public class UserRoomMetadata {
    @Id
    private String id;
    private String userKey;
    
    private String roomId;

    private String lastDeliveredId; // Latest archiveId received by device (XEP-0184)
    private String lastReadId;      // Latest archiveId displayed to user (XEP-0333)
    
    private Instant lastUpdated;
}