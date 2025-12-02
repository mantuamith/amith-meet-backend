package com.algomeet.signalservice.exceptions;

public class OneTimePreKeyIsNotAvailableException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public OneTimePreKeyIsNotAvailableException(String message) {
		super(message);
	}
}
