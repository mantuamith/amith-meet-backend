package com.algomeet.xmpp.chatservice.document;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.persistence.Id;
import lombok.Data;

@Data
@Document(collection = "muc_unread_counts")
public class MucUnreadCount {    
    /**
     * Format: <recipient user key>_<room Id>
     */
    @Id
    private String id;
    
    @Indexed
    @Field("user_key")
    private String userKey; 
    
    @Field("room_id")
    private Long roomId; 
    
    @Field("unread_count")
    private int unreadCount = 0;
}
