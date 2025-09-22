package com.algomeet.authservice.client;

import com.algomeet.authservice.dto.SearchUsersFilter;
import com.algomeet.authservice.dto.UserRequest;
import com.algomeet.authservice.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;


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

    @PostMapping("/internal/users/{id}/session")
    Map<String, String> startSession(@PathVariable("id") Long id,
                                     @RequestParam("deviceId") String deviceId,
                                     @RequestParam(value = "sid", required = false) String sid);

    @GetMapping("/internal/users/active-sid")
    Map<String, String> getActiveSid(@RequestParam("email") String email);

    @GetMapping("/internal/users/exists")
    Map<String, Boolean> checkExists(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String phone
    );



    // tiny helper
    default String getActiveSessionIdByEmail(String email) {
        Map<String, String> res = getActiveSid(email);
        return res == null ? null : res.get("sid");
    }
    
    @PostMapping("/internal/users/{id}/update-log-in-device")
    void updateDeviceTypeAndToken(@PathVariable("id") Long id, 
                            @RequestParam("deviceType") String deviceType,
                            @RequestParam("deviceToken") String deviceToken);


    @GetMapping("/internal/users")
    Pageable findAll(SearchUsersFilter filter);
    
    @GetMapping("/internal/users/{id}")
    UserResponse findUserById(@PathVariable Long id);
    
    @GetMapping("/internal/users/userkey/{userKey}")
    UserResponse findUserByUserKey(@PathVariable UUID userKey);
}

