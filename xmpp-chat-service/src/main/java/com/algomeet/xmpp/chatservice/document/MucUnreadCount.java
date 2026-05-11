package com.algomeet.xmpp.chatservice.document;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.persistence.Id;
import lombok.Data;

@Data
@Document(collection = "muc_unread_counts")
@CompoundIndex(name = "user_room_idx", def = "{'user_key': 1, 'room_id': 1}")
public class MucUnreadCount {    
    /**
     * Format: <recipient user key>_<room Id>
     */
    @Id
    private String id;
    
    @Field("user_key")
    private String userKey; 
    
    @Field("room_id")
    private String roomId; 
    
    @Field("unread_count")
    private int unreadCount = 0;
    
    /** Timestamp of the last increment (new message received). 
     * Used to determine the 'freshness' of the unread count.
     */
    @Indexed
    @Field("last_increment_at")
    private Long lastIncrementAt;	
	
    /** Timestamp of the last decrement (user read the chat). 
     * Critical for resolving race conditions between multiple devices.
     */
    @Field("last_decrement_at")
    private Long lastDecrementAt;

    /**
     * Optional but Recommended: 
     * Stores the ID of the last message that triggered a decrement.
     * Prevents "double-decrement" logic errors.
     */
    @Field("last_read_mid")
    private String lastReadMid;
}
