package com.algomeet.xmpp.chatservice.dto;

import lombok.Data;

@Data
public class MucUnreadCount {
    private String id;
    
    private String userKey; 
    private String roomId; 

    private int unreadCount = 0;

    /**
     * Optional but Recommended: 
     * Stores the ID of the last message that triggered a decrement.
     * Prevents "double-decrement" logic errors.
     */
    private String lastReadMid;
}
