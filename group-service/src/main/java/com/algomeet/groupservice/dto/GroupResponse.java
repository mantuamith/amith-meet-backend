package com.algomeet.groupservice.dto;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.algomeet.groupservice.enums.GroupRole;
import lombok.Data;

@Data
public class GroupResponse {
    private UUID id;
    private String name;
    private String description;
    private String ownerUserKey;
    private Set<MemberResponse> members = new HashSet<>();
    private Long createdAt;
    private Map<GroupRole, RolePermissionsResponse> rolePermissions = new EnumMap<>(GroupRole.class);
    private Integer messageRetentionDays;
}
