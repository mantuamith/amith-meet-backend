package com.algomeet.controlservice.exception;

public class RoleIdAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public RoleIdAlreadyExistsException(String message) {
		super(message);
	}
}
