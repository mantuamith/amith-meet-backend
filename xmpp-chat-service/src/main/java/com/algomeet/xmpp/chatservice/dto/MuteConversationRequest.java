package com.algomeet.xmpp.chatservice.dto;

import lombok.Data;

@Data
public class MuteConversationRequest extends ConversationPreferenceRequest{
	/** Total hours conversation muted */
	private Integer muteUntil;
}
