package com.algomeet.xmpp.chatservice.exceptions;

public class GroupNotFoundException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	
	public GroupNotFoundException(String msg) {
		super(msg);
	}
	
	public GroupNotFoundException(String msg, Exception ex) {
		super(msg, ex);
	}
}
