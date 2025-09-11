package com.algomeet.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class VerifySecurityQuestionResponse {
    private boolean valid;
    private String message;
}