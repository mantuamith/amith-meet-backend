package com.algomeet.userservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.userservice.dto.UserE2eeSettingRequest;
import com.algomeet.userservice.dto.UserE2eeSettingResponse;
import com.algomeet.userservice.model.UserE2eeSetting;
import com.algomeet.userservice.repository.UserE2eeSettingRepository;

@RestController
@RequestMapping("/internal/user-e2ee-settings")
public class UserE2eeSettingController {

    private final UserE2eeSettingRepository repository;

    public UserE2eeSettingController(UserE2eeSettingRepository repository) {
        this.repository = repository;
    }

    // Get a single user setting by userKey
    @GetMapping("/{userKey}")
    public ResponseEntity<UserE2eeSettingResponse> getById(@PathVariable UUID userKey) {
        return repository.findById(userKey)
                .map(setting -> ResponseEntity.ok(mapToResponse(setting)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Create or update user setting
    @PostMapping("/{userKey}")
    public ResponseEntity<UserE2eeSettingResponse> createOrUpdate(
            @PathVariable UUID userKey,
            @RequestBody UserE2eeSettingRequest request) {

        UserE2eeSetting setting = repository.findById(userKey)
                .orElse(new UserE2eeSetting());
        
        if (setting.getUserKey() != null) {
        	if (request.getAutoSyncEnabled() != null) {
        		setting.setAutoSyncEnabled(request.getAutoSyncEnabled());
        	}
        	
        	if (request.getPinConfigured() != null) {
        		setting.setPinConfigured(request.getPinConfigured());
        	}
        	
        	if (request.getPasscodeConfigured() != null) {
        		setting.setPasscodeConfigured(request.getPasscodeConfigured());
        	}
        	
        	if (request.getArgon2Config() != null) {
        		setting.setArgon2Config(request.getArgon2Config());
        	}
        } else {
        	setting.setUserKey(userKey);
        	setting.setAutoSyncEnabled(request.getAutoSyncEnabled());
        	setting.setPinConfigured(request.getPinConfigured());
        	setting.setPasscodeConfigured(request.getPasscodeConfigured());
        	setting.setArgon2Config(request.getArgon2Config());
        }

        UserE2eeSetting saved = repository.save(setting);
        return ResponseEntity.ok(mapToResponse(saved));
    }

    // Delete user setting
    @DeleteMapping("/{userKey}")
    public ResponseEntity<Void> delete(@PathVariable UUID userKey) {
        if (!repository.existsById(userKey)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(userKey);
        return ResponseEntity.noContent().build();
    }

    // Mapper
    private UserE2eeSettingResponse mapToResponse(UserE2eeSetting entity) {
        return new UserE2eeSettingResponse(
                entity.getUserKey(),
                entity.getAutoSyncEnabled(),
                entity.getPinConfigured(),
                entity.getPasscodeConfigured(),
                entity.getArgon2Config()
        );
    }
}