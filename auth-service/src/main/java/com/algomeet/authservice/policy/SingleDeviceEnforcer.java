package com.algomeet.authservice.policy;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SingleDeviceEnforcer {

    private SingleDeviceEnforcer() {}

    public static void enforce(boolean enabled, String userActiveDeviceId, String incomingDeviceId) {
        if (!enabled) return;
        if (userActiveDeviceId == null || userActiveDeviceId.isBlank()) return;
        if (!userActiveDeviceId.equals(incomingDeviceId)) {
            log.warn("Single-device check failed: activeDeviceId={}, incomingDeviceId={}",
                    userActiveDeviceId, incomingDeviceId);
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Account is locked to a different device. Use the bound device or revoke the existing lock."
            );
        }
    }
}
