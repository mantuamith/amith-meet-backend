package com.algomeet.signalservice.dto;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class E2eeEvent {
	private String sourceUserKey;	
	private Integer deviceId;
	private Set<String> subscribers;
}
