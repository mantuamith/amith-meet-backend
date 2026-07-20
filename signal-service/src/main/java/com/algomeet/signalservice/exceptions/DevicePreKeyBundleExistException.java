package com.algomeet.signalservice.exceptions;

public class DevicePreKeyBundleExistException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	public DevicePreKeyBundleExistException(String message) {
		super(message);
	}
}
