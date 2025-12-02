package com.algomeet.signalservice.exceptions;

public class GroupSessionBackupExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public GroupSessionBackupExistsException(String message) {
		super(message);
	}
}
