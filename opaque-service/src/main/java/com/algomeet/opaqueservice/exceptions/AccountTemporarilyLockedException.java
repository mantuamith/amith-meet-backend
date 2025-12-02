package com.algomeet.opaqueservice.exceptions;

public class AccountTemporarilyLockedException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	public AccountTemporarilyLockedException(String message) {
		super(message);
	}
}
