package com.algomeet.signalservice.service;

import com.algomeet.common.dto.ConversationSettings;
import com.algomeet.common.service.ConversationIdProvider;

public interface ConversationSettingsService extends ConversationIdProvider {
	ConversationSettings getSettings(String conversationId);
}
