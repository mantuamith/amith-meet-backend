package com.algomeet.xmpp.chatservice.document;

import org.springframework.data.mongodb.core.mapping.Document;

import com.algomeet.common.document.ConversationSettingDocument;

@Document(collection = "conversation_settings")
public class ConversationSetting extends ConversationSettingDocument {	
	public ConversationSetting(String id, Integer messageRetentionDays) {
		super(id, messageRetentionDays);
	}
	
}
