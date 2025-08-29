package com.algomeet.authservice.client;

import com.algomeet.authservice.dto.UserRequest;
import com.algomeet.authservice.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@FeignClient(name = "user-service", url = "${feign.client.user-service.url}")

public interface UserClient {
    @PostMapping("/internal/users")
    Map<String, Object> createUser(@RequestBody UserRequest request);

    // Reads return plain DTOs (no wrapper)
    @GetMapping("/internal/users/username/{username}")
    UserResponse getUserByUsername(@PathVariable String username);

    @GetMapping("/internal/users/email/{email}")
    UserResponse getUserByEmail(@PathVariable("email") String email);

    // Device bind: switch to POST to avoid PATCH transport issues
    @PostMapping("/internal/users/{id}/active-device")
    void updateActiveDevice(@PathVariable("id") Long id,
                            @RequestParam("deviceId") String deviceId);

    @DeleteMapping("/internal/users/email/{email}")
    void deleteUserByEmail(@PathVariable("email") String email);

    @PutMapping("/internal/users/{id}/password")
    void updatePassword(@PathVariable("id") Long id, @RequestParam("passwordHash") String passwordHash);


    @GetMapping("/internal/users/lookup")
    UserResponse getUserByLogin(@RequestParam("login") String login);


}

