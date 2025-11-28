package com.algomeet.signalservice.exceptions;

public class OneTimePreKeyAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public OneTimePreKeyAlreadyExistsException(String message) {
		super(message);
	}
}
