package com.algomeet.xmpp.chatservice.document;

import org.springframework.data.annotation.Id; // Corrected Import
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;

@Data
@Document(collection = "unread_counts")
@CompoundIndex(name = "user_inbox_idx", def = "{'user_key': 1, 'last_increment_at': -1}")
public class UnreadCount {    
	@Id
	private String id; // format: <senderKey>_<recipientKey>

	@Indexed
	@Field("user_key")
	private String userKey; // The person who OWNS this unread count

	@Indexed
	@Field("sender_key")
	private String senderKey; // The person who SEND this unread count

	@Field("unread_count")
	private int unreadCount = 0;
		
	/** * Timestamp of the last increment (new message received). 
     * Used to determine the 'freshness' of the unread count.
     */
    @Field("last_increment_at")
    private Long lastIncrementAt;	
	
    /** * Timestamp of the last decrement (user read the chat). 
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