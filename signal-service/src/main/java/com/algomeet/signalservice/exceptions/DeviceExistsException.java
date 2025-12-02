package com.algomeet.signalservice.exceptions;

public class DeviceExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public DeviceExistsException(String message) {
		super(message);
	}
}
