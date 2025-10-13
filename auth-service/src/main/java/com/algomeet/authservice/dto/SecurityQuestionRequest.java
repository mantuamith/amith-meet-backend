package com.algomeet.authservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecurityQuestionRequest {
	@NotEmpty(message = "{security-question.empty-id}")
	private String id;
	@NotEmpty
	@NotEmpty(message = "{security-question.empty-question}")
    private String question;
}