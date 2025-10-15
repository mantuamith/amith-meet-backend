package com.algomeet.signalingservice.exceptions;

public class UserDontHaveOneTimeKeyAvailableException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public UserDontHaveOneTimeKeyAvailableException(String message) {
		super(message);
	}
}
