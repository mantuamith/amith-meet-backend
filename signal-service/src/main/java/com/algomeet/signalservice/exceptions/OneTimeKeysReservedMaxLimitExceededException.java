package com.algomeet.signalservice.exceptions;

public class OneTimeKeysReservedMaxLimitExceededException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	public OneTimeKeysReservedMaxLimitExceededException(String message) {
		super(message);
	}
}
