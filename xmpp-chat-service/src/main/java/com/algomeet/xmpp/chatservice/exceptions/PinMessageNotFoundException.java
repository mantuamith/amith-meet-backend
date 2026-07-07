package com.algomeet.xmpp.chatservice.exceptions;

public class PinMessageNotFoundException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	
	public PinMessageNotFoundException(String msg) {
		super(msg);
	}
	
	public PinMessageNotFoundException(String msg, Exception ex) {
		super(msg, ex);
	}
}