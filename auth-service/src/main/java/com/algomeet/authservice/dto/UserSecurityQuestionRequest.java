package com.algomeet.authservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSecurityQuestionRequest {
	@NotEmpty
	@Pattern(
	        regexp = "^[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[1-5][0-9a-fA-F]{3}\\-[89abAB][0-9a-fA-F]{3}\\-[0-9a-fA-F]{12}$",
	        message = "Must be a valid UUID"
	    )
    private String userProfileId;
	@NotEmpty
    private String securityQuestionId; // reference to SecurityQuestions.id
	@NotEmpty
    private String answer;
}