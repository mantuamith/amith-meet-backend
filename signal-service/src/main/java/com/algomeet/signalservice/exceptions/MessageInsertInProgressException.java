package com.algomeet.signalservice.exceptions;

public class MessageInsertInProgressException extends RuntimeException {
    private static final long serialVersionUID = 1L;

	public MessageInsertInProgressException() {
        super("Message insert is currently in progress.");
    }
}