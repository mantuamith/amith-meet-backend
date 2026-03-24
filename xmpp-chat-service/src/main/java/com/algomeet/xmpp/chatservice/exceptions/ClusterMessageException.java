package com.algomeet.xmpp.chatservice.exceptions;

public class ClusterMessageException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	
	public ClusterMessageException(Exception ex) {
		super(ex);
	}
	
	public ClusterMessageException(String msg, Exception ex) {
		super(msg, ex);
	}
}
