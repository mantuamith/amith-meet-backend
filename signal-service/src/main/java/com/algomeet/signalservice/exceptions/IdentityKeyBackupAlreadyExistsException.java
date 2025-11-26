package com.algomeet.signalservice.exceptions;

public class IdentityKeyBackupAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public IdentityKeyBackupAlreadyExistsException(String message) {
		super(message);
	}
}
