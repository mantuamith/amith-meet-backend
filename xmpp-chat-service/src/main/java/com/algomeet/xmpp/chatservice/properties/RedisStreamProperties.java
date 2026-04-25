package com.algomeet.xmpp.chatservice.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
public class RedisStreamProperties {
	@Value("${stream.missed-call.key:missed-call-stream}")
	private String streamMissedCallKey;
}
