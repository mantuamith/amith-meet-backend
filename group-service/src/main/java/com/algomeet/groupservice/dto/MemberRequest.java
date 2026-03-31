package com.algomeet.groupservice.dto;

import com.algomeet.groupservice.enums.GroupRole;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MemberRequest {
	@NotBlank
	private String userKey;
	
	@NotBlank
	private String username;
	
	private String nikname;
	
    private GroupRole role;
}
