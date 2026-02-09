package com.algomeet.groupservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MemberRequest {
	@NotBlank
	private String userKey;
	
	@NotBlank
	private String username;
}
