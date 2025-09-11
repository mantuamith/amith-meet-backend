package com.algomeet.authservice.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserSecurityQuestionResponse {
    private int id;
    private UUID userProfileId;
    private String securityQuestionId;
    private String question;
    private String answer;
}