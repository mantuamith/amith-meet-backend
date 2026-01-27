package com.algomeet.mediaservice.exceptions;

public class FileTypeNotSupportedException extends RuntimeException{

	private static final long serialVersionUID = 1L;
	public FileTypeNotSupportedException(String message) {
		super(message);
	}
}
