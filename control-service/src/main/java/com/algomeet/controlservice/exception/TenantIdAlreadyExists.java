package com.algomeet.controlservice.exception;

public class TenantIdAlreadyExists extends RuntimeException{
	public TenantIdAlreadyExists(String message) {
		super(message);
	}
}
