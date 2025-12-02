package com.algomeet.signalservice.exceptions;

public class OneTimePreKeyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public OneTimePreKeyExistsException(String message) {
		super(message);
	}
}
