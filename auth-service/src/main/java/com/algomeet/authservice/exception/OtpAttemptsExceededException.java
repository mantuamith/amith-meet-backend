package com.algomeet.authservice.exception;

public class OtpAttemptsExceededException extends RuntimeException {
    public OtpAttemptsExceededException(String m) {
        super(m);
    }
}
