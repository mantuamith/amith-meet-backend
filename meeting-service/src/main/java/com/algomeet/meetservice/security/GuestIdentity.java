// com.algomeet.meetservice.security.GuestIdentity
package com.algomeet.meetservice.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

public final class GuestIdentity {
    private GuestIdentity() {}

    private static final String COOKIE = "algomeet_guest_id";
    private static final String CLIENT_HEADER = "x-algomeet-client-id";

    /**
     * Returns a stable guestKey for this device:
     * 1) Prefer x-algomeet-client-id header (if FE sends one from localStorage).
     * 2) Else fallback to HttpOnly cookie.
     * 3) Else generate a new UUID and set cookie.
     */
    public static String resolve(HttpServletRequest req, HttpServletResponse res) {
        // 1) header wins (lets you dedupe across tabs without new cookies)
        String fromHeader = req.getHeader(CLIENT_HEADER);
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader.trim();
        }

        // 2) existing cookie
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }

        // 3) create cookie
        String guestId = UUID.randomUUID().toString();
        Cookie ck = new Cookie(COOKIE, guestId);
        ck.setHttpOnly(true);
        ck.setPath("/");
        ck.setMaxAge(60 * 60 * 24 * 365); // 1 year
        res.addCookie(ck);
        return guestId;
    }
}
