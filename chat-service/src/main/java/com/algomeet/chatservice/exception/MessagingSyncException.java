package com.algomeet.chatservice.exception;

public class MessagingSyncException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	
	public MessagingSyncException(Exception ex) {
		super(ex);
	}
	
	public MessagingSyncException(String msg, Exception ex) {
		super(msg, ex);
	}
}
