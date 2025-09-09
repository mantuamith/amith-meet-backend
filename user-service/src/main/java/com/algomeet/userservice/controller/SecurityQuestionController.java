package com.algomeet.userservice.controller;

import com.algomeet.userservice.dto.SecurityQuestionRequest;
import com.algomeet.userservice.dto.SecurityQuestionResponse;
import com.algomeet.userservice.model.SecurityQuestions;
import com.algomeet.userservice.repository.SecurityQuestionRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/security-questions")
public class SecurityQuestionController {

    private final SecurityQuestionRepository repository;

    public SecurityQuestionController(SecurityQuestionRepository repository) {
        this.repository = repository;
    }

    // Create
    @PostMapping
    public ResponseEntity<SecurityQuestionResponse> create(@RequestBody SecurityQuestionRequest request) {
        SecurityQuestions entity = new SecurityQuestions(
                UUID.randomUUID().toString(),
                request.getQuestion()
        );
        SecurityQuestions saved = repository.save(entity);
        SecurityQuestionResponse response = new SecurityQuestionResponse(saved.getId(), saved.getQuestion());
        return ResponseEntity
                .created(URI.create("/api/security-questions/" + saved.getId()))
                .body(response);
    }

    // Get by ID
    @GetMapping("/{id}")
    public ResponseEntity<SecurityQuestionResponse> getById(@PathVariable String id) {
        return repository.findById(id)
                .map(q -> ResponseEntity.ok(new SecurityQuestionResponse(q.getId(), q.getQuestion())))
                .orElse(ResponseEntity.notFound().build());
    }

    // Get all
    @GetMapping
    public ResponseEntity<List<SecurityQuestionResponse>> getAll() {
        List<SecurityQuestionResponse> responses = repository.findAll().stream()
                .map(q -> new SecurityQuestionResponse(q.getId(), q.getQuestion()))
                .toList();
        return ResponseEntity.ok(responses);
    }

    // Update (PUT = full replace)
    @PutMapping("/{id}")
    public ResponseEntity<SecurityQuestionResponse> update(
            @PathVariable String id,
            @RequestBody SecurityQuestionRequest request) {
        return repository.findById(id)
                .map(existing -> {
                    existing.setQuestion(request.getQuestion());
                    SecurityQuestions updated = repository.save(existing);
                    return ResponseEntity.ok(new SecurityQuestionResponse(updated.getId(), updated.getQuestion()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}