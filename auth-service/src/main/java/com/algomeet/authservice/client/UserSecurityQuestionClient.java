package com.algomeet.authservice.client;


import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.authservice.dto.UserSecurityQuestionRequest;
import com.algomeet.authservice.dto.UserSecurityQuestionResponse;

@FeignClient(name = "user-security-questions-user-service", url = "${feign.client.user-service.url}")
public interface UserSecurityQuestionClient {

    @PostMapping("/internal/user-security-questions")
    public ResponseEntity<UserSecurityQuestionResponse> create(@RequestBody UserSecurityQuestionRequest request);

    @GetMapping("/internal/user-security-questions/{userProfileId}")
    public ResponseEntity<List<UserSecurityQuestionResponse>> getByUserProfileId(@PathVariable UUID userProfileId);

    @DeleteMapping("/internal/user-security-questions/{userProfileId}")
    public ResponseEntity<Void> deleteByUserProfileId(@PathVariable UUID userProfileId);
    
    @GetMapping("/internal/user-security-questions/{userProfileId}/{securityQuestionId}")
    public ResponseEntity<UserSecurityQuestionResponse> getByUserProfileIdAndQuestionId(
            @PathVariable UUID userProfileId,
            @PathVariable String securityQuestionId);
    
    @PutMapping("/internal/user-security-questions/{userProfileId}/{securityQuestionId}")
    public ResponseEntity<UserSecurityQuestionResponse> updateAnswer(
            @PathVariable UUID userProfileId,
            @PathVariable String securityQuestionId,
            @RequestBody UserSecurityQuestionRequest request);    
}