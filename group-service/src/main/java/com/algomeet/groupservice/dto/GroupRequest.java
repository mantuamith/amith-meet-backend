package com.algomeet.groupservice.dto;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;

@Data
public class GroupRequest {
	private String name;
	private String ownerUserKey;
	
	private Set<MemberRequest> members = new HashSet<>();
    private boolean emptyGroup;
}
