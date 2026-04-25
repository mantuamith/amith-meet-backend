package com.algomeet.xmpp.chatservice.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "xmpp.server")
public class DomainProperties {	
    private String domain;

	private String groupChatDomain;
}
