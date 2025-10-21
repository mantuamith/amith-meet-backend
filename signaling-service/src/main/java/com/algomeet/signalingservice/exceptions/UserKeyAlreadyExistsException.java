package com.algomeet.signalingservice.exceptions;

public class UserKeyAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public UserKeyAlreadyExistsException(String message) {
		super(message);
	}
}
