package com.algomeet.controlservice.exception;

public class RoleNameAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public RoleNameAlreadyExistsException(String message) {
		super(message);
	}
}
