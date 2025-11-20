package com.algomeet.authservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecurityQuestionRequest {
	@NotEmpty(message = "{security-question.empty-id}")
	@Size(max = 16)
	private String id;

	@Size(max = 255)
	@NotEmpty(message = "{security-question.empty-question}")
    private String question;
}