package com.algomeet.chatservice.document;

import java.util.List;
import java.util.Map;

import com.algomeet.chatservice.dto.Member;
import com.algomeet.chatservice.dto.RolePermissionsDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupDto {
    public String id;
    public String name;
    public List<Member> members;
    public Map<String, RolePermissionsDto> rolePermissions;
}
