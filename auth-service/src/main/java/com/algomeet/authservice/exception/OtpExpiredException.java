package com.algomeet.authservice.exception;

public class OtpExpiredException extends RuntimeException {
    public OtpExpiredException(String m){super(m);
    }
}
