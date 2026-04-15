package com.algomeet.groupservice.dto;

import java.util.EnumMap;
import java.util.Map;

import com.algomeet.groupservice.enums.GroupRole;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
@Schema(description = "Partial group role-permission update request. Only supplied roles and fields are merged.")
public class GroupPermissionsPatchRequest {

    @Valid
    @NotEmpty
    @Schema(
            description = "Role permissions keyed by group role. Supported roles: OWNER, ADMIN, MEMBER.",
            example = """
                    {
                      "ADMIN": {
                        "approveNewMembers": true
                      },
                      "MEMBER": {
                        "sendNewMessages": false
                      }
                    }
                    """
    )
    private Map<GroupRole, RolePermissionsPatchRequest> rolePermissions = new EnumMap<>(GroupRole.class);
}
