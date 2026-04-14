package com.algomeet.groupservice.dto;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;

@Data
public class GroupResponse {
    private Long id;
    private String name;
    private String description;
    private String ownerUserKey;
    private Set<MemberResponse> members = new HashSet<>();

}
