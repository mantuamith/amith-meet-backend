package com.algomeet.notificationservice.dto;

import com.algomeet.notificationservice.enums.MessageType;

import lombok.Data;

@Data
public class ExchangeMessage {
	protected MessageType type;
}
