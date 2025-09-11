package com.algomeet.authservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifySecurityQuestionRequest {
	@NotEmpty
    private String answer;
}