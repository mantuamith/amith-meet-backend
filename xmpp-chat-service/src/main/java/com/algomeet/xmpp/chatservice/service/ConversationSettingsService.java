package com.algomeet.xmpp.chatservice.service;

import com.algomeet.common.dto.ConversationSettings;
import com.algomeet.common.service.ConversationIdProvider;

import reactor.core.publisher.Mono;

public interface ConversationSettingsService extends ConversationIdProvider {
	Mono<ConversationSettings> getSettings(String conversationId);
}
