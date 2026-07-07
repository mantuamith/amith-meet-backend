package com.algomeet.xmpp.chatservice.document;

import org.springframework.data.mongodb.core.mapping.Document;

import com.algomeet.common.document.ConversationSettingDocument;

@Document(collection = "conversation_settings")
public class ConversationSettingsDocument extends ConversationSettingDocument {	
	public ConversationSettingsDocument(String id, Integer messageRetentionDays) {
		super(id, messageRetentionDays);
	}
	
	public void setMessageRetentionDays(Integer messageRetentionDays) {
		this.messageRetentionDays = messageRetentionDays;
	}
}
