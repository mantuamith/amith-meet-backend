package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.dto.UserResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final UserClient userClient;

    /** Find by login (email or username per your user-service mapping). */
    public UserResponse findByLoginOr404(String login) {
        if (login == null || login.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "login is required");
        }
        final String key = login.trim();
        try {
            UserResponse user = userClient.getUserByEmail(key); // your endpoint handles email OR username
            if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            return user;
        } catch (FeignException.NotFound nf) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }
}
