package com.algomeet.xmpp.chatservice.util;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JidUtil {
    private static final String DOMAIN_SEPARATOR = "@";
    private static final String NICKNAME_SEPARATOR = "/";
    
    // 1. Remove 'static' so Spring can inject this
    private final DomainProperties domainProperties;
    
    // 2. Remove 'static' from methods so they can access the instance field
    public String getBareJid(String userKey) {
        return (userKey + DOMAIN_SEPARATOR + domainProperties.getDomain());
    }
    
    public String getGroupBareJid(String groupId) {
        return (groupId + DOMAIN_SEPARATOR + domainProperties.getGroupChatDomain());
    }
    
    public String getNickname(String roomJid) {
    	if (!(StringUtils.hasText(roomJid))) {
    		return null;
    	}
    	
    	String roomJidArr[] = roomJid.split(NICKNAME_SEPARATOR);
    	if(roomJidArr.length > 1) {
    		return roomJidArr[1];
    	}
    	
    	return null;
    }
}