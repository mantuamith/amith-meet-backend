package com.algomeet.signalingservice.exceptions;

public class DeviceIdAlreadyExistsException extends RuntimeException{
	private static final long serialVersionUID = 1L;

	public DeviceIdAlreadyExistsException(String message) {
		super(message);
	}
}
