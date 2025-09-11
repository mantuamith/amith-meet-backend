package com.algomeet.authservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.algomeet.authservice.dto.SecurityQuestionRequest;
import com.algomeet.authservice.dto.SecurityQuestionResponse;

@FeignClient(name = "security-questions-user-service", url = "${feign.client.user-service.url}")
public interface SecurityQuestionClient {
    // Create
    @PostMapping("/internal/security-questions")
    public ResponseEntity<SecurityQuestionResponse> create(@RequestBody SecurityQuestionRequest request);

    // Get by ID
    @GetMapping("/internal/security-questions/{id}")
    public ResponseEntity<SecurityQuestionResponse> getById(@PathVariable String id);
    
    // Get all
    @GetMapping("/internal/security-questions")
    public ResponseEntity<List<SecurityQuestionResponse>> getAll();

    // Update (PUT = full replace)
    @PutMapping("/internal/security-questions/{id}")
    public ResponseEntity<SecurityQuestionResponse> update(
            @PathVariable String id,
            @RequestBody SecurityQuestionRequest request);

    // Delete
    @DeleteMapping("/internal/security-questions/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id);
}