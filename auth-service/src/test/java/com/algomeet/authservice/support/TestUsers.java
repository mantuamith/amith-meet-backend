// src/test/java/com/algomeet/authservice/testsupport/TestUsers.java
package com.algomeet.authservice.support;

import com.algomeet.authservice.dto.UserResponse;

import java.util.UUID;

public final class TestUsers {
    private TestUsers() {}

    public static UserResponse user(Long id, String username, String email) {
        UserResponse u = new UserResponse();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        u.setUserKey(UUID.randomUUID());
        return u;
    }
}
