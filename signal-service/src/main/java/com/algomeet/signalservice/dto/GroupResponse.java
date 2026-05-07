package com.algomeet.signalservice.dto;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class GroupResponse {
    private UUID id;
    private String name;
    private String description;
    private String ownerUserKey;
    private Set<MemberResponse> members = new HashSet<>();

}
