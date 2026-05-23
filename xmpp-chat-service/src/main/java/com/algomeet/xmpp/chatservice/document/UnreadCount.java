package com.algomeet.xmpp.chatservice.document;

import java.util.UUID;

import org.springframework.data.annotation.Id; // Corrected Import
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;

@Data
@Document(collection = "unread_counts")
@CompoundIndexes({
	/**
	 * Used for UnreadCountService.syncUnreadCount
	 */
	/**
     * Covers: 
     * - UnreadCountService.getTotalUnreadForUser(userKey)
     * - UnreadCountService.getUnreadCountsForUser(recipientKey) [via index-bound range filtering]
     * - Left-leg optimization of UnreadCountService.getRecentContactKeysReactive ($or clause matching user_key)
     */
	@CompoundIndex(name = "idx_unread_user_timeline", def = "{'user_key': 1, 'last_increment_at': -1}"),
	
	/**
     * Covers:
     * - Right-leg optimization of UnreadCountService.getRecentContactKeysReactive ($or clause matching sender_key)
     * 
     * Ensures that the parallel sorting on 'last_increment_at DESC' runs instantly 
     * via index intersection with no in-memory sorting penalties.
     */
	@CompoundIndex(name = "idx_unread_sender_timeline", def = "{'sender_key': 1, 'last_increment_at': -1}")
})
public class UnreadCount {    
	@Id
	private String id; // format: <senderKey>_<recipientKey>

	@Indexed
	@Field("user_key")
	private UUID userKey; // The person who OWNS this unread count

	@Indexed
	@Field("sender_key")
	private UUID senderKey; // The person who SEND this unread count

	@Field("unread_count")
	private int unreadCount = 0;
		
	/** * Timestamp of the last increment (new message received). 
     * Used to determine the 'freshness' of the unread count.
     */
    @Indexed
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
    /** Last Read Message ID **/
    @Field("last_read_mid")
    private UUID lastReadMid;
    
    /** Last Read stanza ID **/
    @Field("last_read_sid")
    private UUID lastReadSid;
}