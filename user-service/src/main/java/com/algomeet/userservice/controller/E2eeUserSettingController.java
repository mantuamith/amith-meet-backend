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

import com.algomeet.userservice.dto.E2eeUserSettingRequest;
import com.algomeet.userservice.dto.E2eeUserSettingResponse;
import com.algomeet.userservice.model.E2eeUserSetting;
import com.algomeet.userservice.repository.E2eeUserSettingRepository;

@RestController
@RequestMapping("/internal/e2ee-user-settings")
public class E2eeUserSettingController {

    private final E2eeUserSettingRepository repository;

    public E2eeUserSettingController(E2eeUserSettingRepository repository) {
        this.repository = repository;
    }

    // Get a single user setting by userKey
    @GetMapping("/{userKey}")
    public ResponseEntity<E2eeUserSettingResponse> getById(@PathVariable UUID userKey) {
        return repository.findById(userKey)
                .map(setting -> ResponseEntity.ok(mapToResponse(setting)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Create or update user setting
    @PostMapping("/{userKey}")
    public ResponseEntity<E2eeUserSettingResponse> createOrUpdate(
            @PathVariable UUID userKey,
            @RequestBody E2eeUserSettingRequest request) {

        E2eeUserSetting setting = repository.findById(userKey)
                .orElse(new E2eeUserSetting());
        
        if (setting.getUserKey() != null) {
        	if (StringUtils.hasLength(request.getAutoSyncKey())) {
        		setting.setAutoSyncKey(request.getAutoSyncKey());
        	}

        	if (request.getAutoSyncEnabled() != null) {
        		setting.setAutoSyncEnabled(request.getAutoSyncEnabled());
        	}
        } else {
        	setting.setUserKey(userKey);
        	setting.setAutoSyncKey(request.getAutoSyncKey());
        	setting.setAutoSyncEnabled(request.getAutoSyncEnabled());
        }

        E2eeUserSetting saved = repository.save(setting);
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
    private E2eeUserSettingResponse mapToResponse(E2eeUserSetting entity) {
        return new E2eeUserSettingResponse(
                entity.getUserKey(),
                entity.getAutoSyncKey(),
                entity.getAutoSyncEnabled()
        );
    }
}