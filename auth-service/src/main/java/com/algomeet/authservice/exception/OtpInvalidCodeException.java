package com.algomeet.authservice.exception;

public class OtpInvalidCodeException extends RuntimeException {
    public OtpInvalidCodeException(String m) {
        super(m);
    }
}
