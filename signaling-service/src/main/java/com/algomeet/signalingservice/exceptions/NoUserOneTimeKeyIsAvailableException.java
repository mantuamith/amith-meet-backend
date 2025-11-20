package com.algomeet.signalingservice.exceptions;

public class NoUserOneTimeKeyIsAvailableException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public NoUserOneTimeKeyIsAvailableException(String message) {
		super(message);
	}
}
