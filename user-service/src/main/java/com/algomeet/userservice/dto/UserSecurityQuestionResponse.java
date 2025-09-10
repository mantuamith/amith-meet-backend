package com.algomeet.userservice.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserSecurityQuestionResponse {
    private int id;
    private UUID userProfileId;
    private String securityQuestionId;
    private String question;
    private String answer;
}