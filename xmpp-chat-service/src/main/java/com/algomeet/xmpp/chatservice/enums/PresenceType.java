package com.algomeet.xmpp.chatservice.enums;

public enum PresenceType {
	UNAVAILABLE("unavailable");
		
    private final String value;
    
    PresenceType(String value) {
    	this.value = value;
    }
    
    public String getValue() {
    	return this.value;
    }
}
