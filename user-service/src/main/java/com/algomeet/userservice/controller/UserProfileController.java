package com.algomeet.userservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.algomeet.userservice.dto.UserProfileResponse;
import com.algomeet.userservice.dto.UserProfileUpdateRequest;
import com.algomeet.userservice.model.UserProfile;
import com.algomeet.userservice.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileRepository repository;

    // GET user profile
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT = full update (replace all fields)
    @PutMapping("/{id}")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @PathVariable UUID id,
            @RequestBody UserProfileUpdateRequest request) {

        return repository.findById(id)
                .map(existing -> {
                    updateEntity(existing, request); // overwrite all
                    return ResponseEntity.ok(toResponse(repository.save(existing)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // --- Helpers ---
    private void updateEntity(UserProfile entity, UserProfileUpdateRequest request) {
        if (request.getLoginTypePolicy() != null) entity.setLoginTypePolicy(request.getLoginTypePolicy());
        if (request.getCountry() != null) entity.setCountry(request.getCountry());
        if (request.getRegion() != null) entity.setRegion(request.getRegion());
        if (request.getCity() != null) entity.setCity(request.getCity());
        if (request.getLatitude() != null) entity.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) entity.setLongitude(request.getLongitude());
        if (request.getRegistrationDeviceId() != null) entity.setRegistrationDeviceId(request.getRegistrationDeviceId());
        if (request.getRegistrationDeviceType() != null) entity.setRegistrationDeviceType(request.getRegistrationDeviceType());
        if (request.getPasscode() != null) entity.setPasscode(request.getPasscode());
    }

    private UserProfileResponse toResponse(UserProfile entity) {
        return UserProfileResponse.builder()
                .id(entity.getId())
                .loginTypePolicy(entity.getLoginTypePolicy())
                .country(entity.getCountry())
                .region(entity.getRegion())
                .city(entity.getCity())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .registrationDeviceId(entity.getRegistrationDeviceId())
                .registrationDeviceType(entity.getRegistrationDeviceType())
                .registrationDate(entity.getRegistrationDate())
                .passcode(entity.getPasscode())
                .build();
    }
}