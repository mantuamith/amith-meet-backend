package com.algomeet.groupservice.dto;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class GroupDto {
    private Long id;
    private String name;
    private Set<MemberDto> members = new HashSet<>();
}
