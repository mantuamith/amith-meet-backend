package com.algomeet.common.service;

import java.util.UUID;


public interface ConversationIdProvider {
	String getConversationId(UUID userKeyA, UUID userKeyB);
}