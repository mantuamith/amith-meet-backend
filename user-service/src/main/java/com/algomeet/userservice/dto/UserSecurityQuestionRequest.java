package com.algomeet.userservice.dto;

import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSecurityQuestionRequest {
    private UUID userProfileId;
    private String securityQuestionId; // reference to SecurityQuestions.id
    private String answer;
}