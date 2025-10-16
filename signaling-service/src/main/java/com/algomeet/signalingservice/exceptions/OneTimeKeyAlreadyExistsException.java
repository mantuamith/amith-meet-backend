package com.algomeet.signalingservice.exceptions;

public class OneTimeKeyAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public OneTimeKeyAlreadyExistsException(String message) {
		super(message);
	}
}
