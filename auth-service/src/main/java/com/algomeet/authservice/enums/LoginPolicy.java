// src/main/java/com/algomeet/authservice/policy/LoginPolicy.java
package com.algomeet.authservice.enums;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public enum LoginPolicy {
    DIRECT(0),      // no OTP, proceed directly
    EMAIL(1),       // email OTP
    PHONE(2),       // SMS/phone OTP
    TOTP(3);        // authenticator app

    private final int code;

    LoginPolicy(int code) { this.code = code; }

    public int getCode() { return code; }

    public static LoginPolicy fromCode(Number n) {
        if (n == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing login policy code");
        int v = n.intValue();
        for (LoginPolicy p : values()) if (p.code == v) return p;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown login policy code: " + v);
    }
}
