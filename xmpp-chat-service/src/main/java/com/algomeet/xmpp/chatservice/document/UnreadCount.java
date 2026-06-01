package com.algomeet.xmpp.chatservice.document;

import java.util.UUID;

import org.springframework.data.annotation.Id; // Corrected Import
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
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
	@CompoundIndex(name = "idxUnread_userKey_lastIncrementAtDesc", def = "{'userKey': 1, 'lastIncrementAt': -1}"),
	
	/**
     * Covers:
     * - Right-leg optimization of UnreadCountService.getRecentContactKeysReactive ($or clause matching sender_key)
     * 
     * Ensures that the parallel sorting on 'last_increment_at DESC' runs instantly 
     * via index intersection with no in-memory sorting penalties.
     */
	@CompoundIndex(name = "idxUnread_senderKey_lastIncrementAtDesc", def = "{'senderKey': 1, 'lastIncrementAt': -1}")
})
public class UnreadCount {    
	@Id
	private String id; // format: <senderKey>_<recipientKey>

	@Indexed
	private UUID userKey; // The person who OWNS this unread count

	@Indexed
	private UUID senderKey; // The person who SEND this unread count

	private int unreadCount = 0;
		
	/** * Timestamp of the last increment (new message received). 
     * Used to determine the 'freshness' of the unread count.
     */
    @Indexed
    private Long lastIncrementAt;	
	
    /** * Timestamp of the last decrement (user read the chat). 
     * Critical for resolving race conditions between multiple devices.
     */
    private Long lastDecrementAt;

    /**
     * Optional but Recommended: 
     * Stores the ID of the last message that triggered a decrement.
     * Prevents "double-decrement" logic errors.
     */
    /** Last Read Message ID **/
    private UUID lastReadMid;
    
    /** Last Read stanza ID **/
    private UUID lastReadSid;
}