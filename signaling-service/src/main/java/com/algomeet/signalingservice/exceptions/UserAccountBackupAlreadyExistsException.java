package com.algomeet.signalingservice.exceptions;

public class UserAccountBackupAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public UserAccountBackupAlreadyExistsException(String message) {
		super(message);
	}
}
