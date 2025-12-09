package com.algomeet.controlservice.exception;

public class TenantIdAlreadyExistsException extends RuntimeException{
	public TenantIdAlreadyExistsException(String message) {
		super(message);
	}
}
