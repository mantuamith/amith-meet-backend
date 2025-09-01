package com.algomeet.authservice.enums;
public enum OtpChannel { EMAIL, SMS;

    static OtpChannel parse(String s) {
        switch (s.toUpperCase()) {
            case "EMAIL": return EMAIL;
            case "SMS":
            case "PHONE": return SMS;     // alias
            default: throw new IllegalArgumentException("Unknown channel: " + s);
        }
    }
}

