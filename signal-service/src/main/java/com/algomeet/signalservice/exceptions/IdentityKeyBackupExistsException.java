package com.algomeet.signalservice.exceptions;

public class IdentityKeyBackupExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public IdentityKeyBackupExistsException(String message) {
		super(message);
	}
}
