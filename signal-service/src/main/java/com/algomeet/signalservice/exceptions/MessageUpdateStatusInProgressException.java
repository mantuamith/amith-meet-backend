package com.algomeet.signalservice.exceptions;

public class MessageUpdateStatusInProgressException extends RuntimeException {
    private static final long serialVersionUID = 1L;

	public MessageUpdateStatusInProgressException() {
        super("Message update is currently in progress.");
    }
}