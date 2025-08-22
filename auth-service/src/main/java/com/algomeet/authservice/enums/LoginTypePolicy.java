package com.algomeet.authservice.enums;

public enum LoginTypePolicy {
    ANY(0),
    MOBILE(1),
    WEB(2),
    DESKTOP(3);

    private final int code;

    LoginTypePolicy(int code) { this.code = code; }

    public int getCode() { return code; }

    public static LoginTypePolicy fromCode(Number n) {
        int c = (n == null) ? 0 : n.intValue();
        for (LoginTypePolicy p : values()) {
            if (p.code == c) return p;
        }
        return ANY; // safe default
    }

    /** whether this policy allows a given device type */
    public boolean allows(DeviceType dt) {
        return switch (this) {
            case ANY -> true;
            case MOBILE -> dt == DeviceType.ANDROID || dt == DeviceType.IOS;
            case WEB -> dt == DeviceType.WEB;
            case DESKTOP -> dt == DeviceType.DESKTOP;
        };
    }

    /** human-readable allowed set (for error messages) */
    public String allowedText() {
        return switch (this) {
            case ANY -> "[WEB, ANDROID, IOS, DESKTOP]";
            case MOBILE -> "[ANDROID, IOS]";
            case WEB -> "[WEB]";
            case DESKTOP -> "[DESKTOP]";
        };
    }
}
