package com.algomeet.userservice.controller;

import com.algomeet.userservice.dto.UserSecurityQuestionRequest;
import com.algomeet.userservice.dto.UserSecurityQuestionResponse;
import com.algomeet.userservice.model.SecurityQuestions;
import com.algomeet.userservice.model.UserSecurityQuestion;
import com.algomeet.userservice.repository.SecurityQuestionRepository;
import com.algomeet.userservice.repository.UserSecurityQuestionRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/user-security-questions")
public class UserSecurityQuestionController {

    private final UserSecurityQuestionRepository answersRepository;
    private final SecurityQuestionRepository questionRepository;

    public UserSecurityQuestionController(UserSecurityQuestionRepository answersRepository,
                                                 SecurityQuestionRepository questionRepository) {
        this.answersRepository = answersRepository;
        this.questionRepository = questionRepository;
    }

    @PostMapping
    public ResponseEntity<UserSecurityQuestionResponse> create(@RequestBody UserSecurityQuestionRequest request) {
        SecurityQuestions question = questionRepository.findById(request.getSecurityQuestionId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid Security question ID"));

        UserSecurityQuestion answer = new UserSecurityQuestion();
        answer.setUserProfileId(request.getUserProfileId());
        answer.setSecurityQuestion(question);
        answer.setAnswer(request.getAnswer());

        UserSecurityQuestion saved = answersRepository.save(answer);

        return ResponseEntity.ok(
                new UserSecurityQuestionResponse(
                        saved.getId(),
                        saved.getUserProfileId(),
                        saved.getSecurityQuestion().getId(),
                        saved.getSecurityQuestion().getQuestion(),
                        saved.getAnswer()
                )
        );
    }

    @GetMapping("/{userProfileId}")
    public ResponseEntity<List<UserSecurityQuestionResponse>> getByUserProfileId(@PathVariable UUID userProfileId) {
        List<UserSecurityQuestion> answers = answersRepository.findByUserProfileId(userProfileId);

        List<UserSecurityQuestionResponse> response = answers.stream()
                .map(a -> new UserSecurityQuestionResponse(
                        a.getId(),
                        a.getUserProfileId(),
                        a.getSecurityQuestion().getId(),
                        a.getSecurityQuestion().getQuestion(),
                        a.getAnswer()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userProfileId}")
    public ResponseEntity<Void> deleteByUserProfileId(@PathVariable UUID userProfileId) {
        answersRepository.deleteByUserProfileId(userProfileId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{userProfileId}/{securityQuestionId}")
    public ResponseEntity<UserSecurityQuestionResponse> getByUserProfileIdAndQuestionId(
            @PathVariable UUID userProfileId,
            @PathVariable String securityQuestionId) {

        return answersRepository.findByUserProfileIdAndSecurityQuestion_Id(userProfileId, securityQuestionId)
                .map(a -> new UserSecurityQuestionResponse(
                        a.getId(),
                        a.getUserProfileId(),
                        a.getSecurityQuestion().getId(),
                        a.getSecurityQuestion().getQuestion(),
                        a.getAnswer()
                ))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    } 
    
    @PutMapping("/{userProfileId}/{securityQuestionId}")
    public ResponseEntity<UserSecurityQuestionResponse> updateAnswer(
            @PathVariable UUID userProfileId,
            @PathVariable String securityQuestionId,
            @RequestBody UserSecurityQuestionRequest request) {

        return answersRepository.findByUserProfileIdAndSecurityQuestion_Id(userProfileId, securityQuestionId)
                .map(existing -> {
                    existing.setAnswer(request.getAnswer());
                    UserSecurityQuestion updated = answersRepository.save(existing);

                    return ResponseEntity.ok(
                            new UserSecurityQuestionResponse(
                                    updated.getId(),
                                    updated.getUserProfileId(),
                                    updated.getSecurityQuestion().getId(),
                                    updated.getSecurityQuestion().getQuestion(),
                                    updated.getAnswer()
                            )
                    );
                })
                .orElse(ResponseEntity.notFound().build());
    }    
}