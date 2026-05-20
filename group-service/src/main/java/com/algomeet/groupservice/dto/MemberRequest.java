package com.algomeet.groupservice.dto;

import com.algomeet.groupservice.enums.GroupRole;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(value = { "memberStartDate" })
public class MemberRequest {
	@NotBlank
	private String userKey;
	
	@NotBlank
	private String username;
	
	@JsonProperty("nickname")
	@JsonAlias("nikname")
	private String nikname;
	
    private GroupRole role;
}
