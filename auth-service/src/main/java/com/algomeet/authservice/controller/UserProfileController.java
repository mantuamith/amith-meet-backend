package com.algomeet.authservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.UserProfileResponse;
import com.algomeet.authservice.dto.UserProfileUpdateRequest;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.service.UserProfileService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {	
	private final UserProfileService userProfileService;

    // GET user profile
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponse<UserProfileResponse>> getProfile(@PathVariable UUID id) {
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, userProfileService.findById(id)));
    }

    // PUT = full update (replace all fields)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponse<UserProfileResponse>> updateProfile(
            @PathVariable UUID id,
            @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.UPDATE_USER_PROFILE_SUCCESS, userProfileService.updateProfile(id, request)));
    }
 }