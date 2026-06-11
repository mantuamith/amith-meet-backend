package com.algomeet.xmpp.chatservice.document;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversation_settings")
public class ConversationSetting {
	/**
     * MongoDB Document Field Names
     */
    public static final String FIELD_ID = "_id";
    public static final String FIELD_EXPIRATION = "expiration";
    
	private String id; // <senderUserKey>_<receiverUserKey>
	private Long expiration;
}
