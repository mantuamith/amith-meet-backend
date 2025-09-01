package com.algomeet.authservice.exception;

public class OtpChannelMismatchException extends RuntimeException {
    public OtpChannelMismatchException(String m) {
        super(m);
    }
}
