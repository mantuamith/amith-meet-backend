package com.algomeet.authservice.exception;

// exceptions (package com.algomeet.authservice.exception)
public class OtpNotFoundException extends RuntimeException {
    public OtpNotFoundException(String m) {
        super(m);
    }
}

