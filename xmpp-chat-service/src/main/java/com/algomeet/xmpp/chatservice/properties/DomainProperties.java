package com.algomeet.xmpp.chatservice.properties;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
public class DomainProperties {
	
    @Value("${xmpp.server.domain}")
    private String domain;

	@Value("${xmpp.server.group-chat-domain}")
	private String groupChatDomain;

}
