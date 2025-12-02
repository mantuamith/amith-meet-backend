package com.algomeet.signalservice.exceptions;


public class MaxSessionsLimitExceededException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public MaxSessionsLimitExceededException(String message) {
		super(message);
	}
}
