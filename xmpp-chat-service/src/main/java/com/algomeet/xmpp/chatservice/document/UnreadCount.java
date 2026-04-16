package com.algomeet.xmpp.chatservice.document;

import org.springframework.data.annotation.Id; // Corrected Import
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import lombok.Data;

@Data
@Document(collection = "unread_counts")
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
		
	@Field("updated_at")
	private Long updatedAt;	
}