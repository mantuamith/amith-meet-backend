package com.algomeet.groupservice.dto;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import lombok.Data;

@Data
public class GroupResponse {
    private UUID id;
    private String name;
    private String description;
    private String ownerUserKey;
    private Set<MemberResponse> members = new HashSet<>();
    private Long createdAt;

}
