package com.algomeet.authservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSecurityQuestionRequest {
	@Deprecated
	@Pattern(
	        regexp = "^[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[1-5][0-9a-fA-F]{3}\\-[89abAB][0-9a-fA-F]{3}\\-[0-9a-fA-F]{12}$",
	        message = "{user-security-question.invalid-user-profile-id}"
	    )
    private String userProfileId;
	@NotEmpty
    private String securityQuestionId; // reference to SecurityQuestions.id
	
	@Size(max = 255)
	@NotEmpty
    private String answer;
}