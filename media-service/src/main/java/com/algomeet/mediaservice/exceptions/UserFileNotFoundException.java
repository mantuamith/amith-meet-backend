package com.algomeet.mediaservice.exceptions;

public class UserFileNotFoundException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	public UserFileNotFoundException(String message) {
		super(message);
	}
}
