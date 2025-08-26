package com.algomeet.authservice.dto;

public enum LoginPolicy {
    DIRECT((short) 0),
    EMAIL_OTP((short) 1),
    PHONE_OTP((short) 2),
    TOTP((short) 3);

    private final short code;

    LoginPolicy(short code) { this.code = code; }
    public short getCode() { return code; }

    public static LoginPolicy fromCode(short code) {
        for (LoginPolicy p : values()) if (p.code == code) return p;
        throw new IllegalArgumentException("Unsupported login policy code: " + code);
    }
}
