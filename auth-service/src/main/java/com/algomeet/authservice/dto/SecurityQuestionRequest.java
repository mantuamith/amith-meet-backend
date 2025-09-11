package com.algomeet.authservice.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecurityQuestionRequest {
	@NotEmpty
	private String id;
	@NotEmpty
    private String question;
}