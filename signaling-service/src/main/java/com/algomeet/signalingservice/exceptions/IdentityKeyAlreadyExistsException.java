package com.algomeet.signalingservice.exceptions;

public class IdentityKeyAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public IdentityKeyAlreadyExistsException(String message) {
		super(message);
	}
}
