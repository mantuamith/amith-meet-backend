package com.algomeet.authservice.client;

import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.authservice.dto.UserProfileResponse;
import com.algomeet.authservice.dto.UserProfileUpdateRequest;

@FeignClient(name = "user-profile-user-service", url = "${feign.client.user-service.url}")
public interface UserProfileClient {

    // GET user profile
    @GetMapping("/internal/user-profiles/{id}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID id);

    // PUT = full update (replace all fields)
    @PutMapping("/internal/user-profiles/{id}")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @PathVariable UUID id,
            @RequestBody UserProfileUpdateRequest request);
}
