package com.algomeet.signalservice.exceptions;

public class GroupSenderKeyBackupExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public GroupSenderKeyBackupExistsException(String message) {
		super(message);
	}
}
