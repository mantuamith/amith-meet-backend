package com.algomeet.userservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.userservice.dto.SecurityQuestionRequest;
import com.algomeet.userservice.dto.SecurityQuestionResponse;
import com.algomeet.userservice.model.SecurityQuestions;
import com.algomeet.userservice.repository.SecurityQuestionRepository;

import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/internal/security-questions")
public class SecurityQuestionController {

    private final SecurityQuestionRepository repository;

    public SecurityQuestionController(SecurityQuestionRepository repository) {
        this.repository = repository;
    }

    // Create
    @PostMapping
    public ResponseEntity<SecurityQuestionResponse> create(@RequestBody SecurityQuestionRequest request) {
        SecurityQuestions entity = new SecurityQuestions(
        		request.getId(),
                request.getQuestion()
        );
        SecurityQuestions saved = repository.save(entity);
        SecurityQuestionResponse response = new SecurityQuestionResponse(saved.getId(), saved.getQuestion());
        return ResponseEntity
                .ok(response);
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
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}