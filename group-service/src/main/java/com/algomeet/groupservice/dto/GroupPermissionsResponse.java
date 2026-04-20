package com.algomeet.groupservice.dto;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import com.algomeet.groupservice.enums.GroupRole;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Current group role-permission settings after merge.")
public class GroupPermissionsResponse {

    @Schema(example = "11111111-1111-1111-1111-111111111111")
    private UUID groupId;

    @Schema(description = "Resolved role permissions keyed by group role.")
    private Map<GroupRole, RolePermissionsResponse> rolePermissions = new EnumMap<>(GroupRole.class);
}
