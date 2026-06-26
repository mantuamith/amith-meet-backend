package com.algomeet.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode 
@NoArgsConstructor
public class ConversationSettings {
	private Integer messageRetentionDays;
	
	public ConversationSettings(Integer messageRetentionDays) {
		this.messageRetentionDays = messageRetentionDays;
	}
}
