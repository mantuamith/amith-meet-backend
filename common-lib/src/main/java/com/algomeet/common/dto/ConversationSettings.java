package com.algomeet.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode 
public class ConversationSettings {
	private Integer messageRetentionDays;
}
