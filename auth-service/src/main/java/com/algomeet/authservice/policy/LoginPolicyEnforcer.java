// src/main/java/com/algomeet/authservice/policy/LoginPolicyEnforcer.java
package com.algomeet.authservice.policy;

import com.algomeet.authservice.enums.DeviceType; // if you have this enum already
import com.algomeet.authservice.enums.LoginPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class LoginPolicyEnforcer {
    private LoginPolicyEnforcer() {}

    /**
     * Enforce compatibility between the selected LoginPolicy and the incoming device type.
     * Throws ResponseStatusException on violations. Returns void otherwise.
     */
    public static void enforce(LoginPolicy policy, DeviceType deviceType) {
        switch (policy) {
            case DIRECT -> { /* no OTP required; any device type is fine */ }
            case EMAIL, PHONE, TOTP -> {
                // Currently no device restrictions; add if you need (e.g., TOTP not allowed on DESKTOP).
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported login policy: " + policy);
        }
    }

    // Convenience overload if your controller receives device type as String
    public static void enforce(LoginPolicy policy, String deviceTypeRaw) {
        DeviceType deviceType;
        try {
            deviceType = DeviceType.valueOf(deviceTypeRaw.toUpperCase());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid deviceType: " + deviceTypeRaw);
        }
        enforce(policy, deviceType);
    }
}
