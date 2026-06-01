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
	@CompoundIndex(name = "idxUnread_userKey_lastIncrementAtDesc", def = "{'userKey': 1, 'lastIncrementAt': -1}"),
	
	/**
     * Covers:
     * - Right-leg optimization of UnreadCountService.getRecentContactKeysReactive ($or clause matching sender_key)
     * 
     * Ensures that the parallel sorting on 'lastIncrementAt DESC' runs instantly 
     * via index intersection with no in-memory sorting penalties.
     */
	@CompoundIndex(name = "idxUnread_senderKey_lastIncrementAtDesc", def = "{'senderKey': 1, 'lastIncrementAt': -1}")
})
public class UnreadCount {   
	// --- Database Field Name Constants (CamelCase) ---
    public static final String ID = "id";
    public static final String USER_KEY = "userKey"; 
    public static final String SENDER_KEY = "senderKey"; 
    public static final String UNREAD_COUNT = "unreadCount";
    public static final String LAST_INCREMENT_AT = "lastIncrementAt";
    public static final String LAST_DECREMENT_AT = "lastDecrementAt";
    public static final String LAST_READ_MID = "lastReadMid";
    public static final String LAST_READ_SID = "lastReadSid";

    @Id
    private String id; // format: <senderKey>_<recipientKey>

    @Indexed
    @Field(USER_KEY)
    private UUID userKey; // The person who OWNS this unread count

    @Field(SENDER_KEY)
    private UUID senderKey; // The person who SENT the messages

    @Field(UNREAD_COUNT)
    private int unreadCount = 0;
		
    /** * Timestamp of the last increment (new message received). 
     * Used to determine the 'freshness' of the unread count.
     */
    @Field(LAST_INCREMENT_AT)
    private Long lastIncrementAt;	
	
    /** * Timestamp of the last decrement (user read the chat). 
     * Critical for resolving race conditions between multiple devices.
     */
    @Field(LAST_DECREMENT_AT)
    private Long lastDecrementAt;

    /** * Last Read Message ID 
     **/
    @Field(LAST_READ_MID)
    private UUID lastReadMid;
    
    /** * Last Read stanza ID 
     **/
    @Field(LAST_READ_SID)
    private UUID lastReadSid;	
}