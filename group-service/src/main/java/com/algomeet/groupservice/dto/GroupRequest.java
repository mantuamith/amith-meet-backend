package com.algomeet.groupservice.dto;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class GroupRequest {
	private String name;
	
	@NotEmpty
	private Set<MemberRequest> members = new HashSet<>();
}
