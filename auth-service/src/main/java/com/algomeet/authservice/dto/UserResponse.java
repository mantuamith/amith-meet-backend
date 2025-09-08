package com.algomeet.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String password;
    private String role;
    private boolean enabled;
    private Short loginTypePolicy;
    private String activeDeviceId;

    @SuppressWarnings("unchecked")
    public UserResponse(Map<String, Object> map) {
        if (map == null) return;

        this.id = map.get("id") != null ? ((Number) map.get("id")).longValue() : null;
        this.username = (String) map.get("username");
        this.email = (String) map.get("email");
        this.password = (String) map.get("password");
        this.activeDeviceId = (String) map.get("activeDeviceId");

        Object ltp = map.get("loginTypePolicy");
        if (ltp instanceof Number) {
            this.loginTypePolicy = ((Number) ltp).shortValue();
        } else if (ltp instanceof String) {
            try {
                this.loginTypePolicy = Short.valueOf((String) ltp);
            } catch (NumberFormatException ignored) {}
        }

        // role & enabled are optional in response
        this.role = (String) map.get("role");
        this.enabled = map.get("enabled") != null && Boolean.TRUE.equals(map.get("enabled"));
    }
}
