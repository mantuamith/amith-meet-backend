package com.algomeet.signalservice.exceptions;

public class DeviceAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public DeviceAlreadyExistsException(String message) {
		super(message);
	}
}
