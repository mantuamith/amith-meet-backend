package com.algomeet.userservice.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.algomeet.userservice.dto.UserProfileResponse;
import com.algomeet.userservice.dto.UserProfileUpdateRequest;
import com.algomeet.userservice.model.User;
import com.algomeet.userservice.model.UserProfile;
import com.algomeet.userservice.repository.UserProfileRepository;
import com.algomeet.userservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/user-profiles")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileRepository repository;
    private final UserRepository userRepository;

    // GET user profile
    @GetMapping("/{id}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable UUID id) {
    	Optional<User> userOpt = userRepository.findByUserKey(id); 
    	if (userOpt.isEmpty()) {
    		return ResponseEntity.notFound().build();
    	}
    	    	
    	Optional<UserProfileResponse> userProfileResponseOpt = repository.findById(id)
                .map(profile -> toResponse(profile, userOpt.get()));   	
      	
    	return userProfileResponseOpt.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // PUT = full update (replace all fields)
    @PutMapping("/{id}")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @PathVariable UUID id,
            @RequestBody UserProfileUpdateRequest request) {
    	
    	final Optional<User> userOpt = userRepository.findByUserKey(id);
    	final User savedUser;
    	
    	if(userOpt.isPresent()) {
    		User user = userOpt.get();
    		if(request.getTenantId() != null) {
    			user.setTenantId(request.getTenantId());
    		}
    		
    		if (request.getRole() != null) {
    			user.setRole(request.getRole());
    		}
    		
    		savedUser = userRepository.save(user);
    	} else {
    		return ResponseEntity.notFound().build();
    	}

        return repository.findById(id)
                .map(existing -> {
                    updateEntity(existing, request); // overwrite all
                    return ResponseEntity.ok(toResponse(repository.save(existing), savedUser));
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
        if (request.getSecurityQuestionsEnabled() != null) entity.setSecurityQuestionsEnabled(request.getSecurityQuestionsEnabled());
    }

    private UserProfileResponse toResponse(UserProfile entity, User user) {       	
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
                .securityQuestionsEnabled(entity.getSecurityQuestionsEnabled())     
                
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .tenantId(user.getTenantId())
                .build();
    }
}