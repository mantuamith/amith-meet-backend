package com.algomeet.groupservice.controller.swagger;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Internal Group API",
    description = "Internal APIs for retrieving group information. Not intended for public use."
)

public interface InternalGroupControllerDoc {

    @Operation(
        summary = "Get group by ID",
        description = "Returns group details for internal service-to-service communication"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Group found and returned"),
        @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public Object getGroup(
            @Parameter(
                description = "Unique identifier of the group",
                example = "1",
                required = true
            )
            @PathVariable Long groupId);

    @Operation(
        summary = "Get groups by member username",
        description = "Returns all groups that contain the given username for internal service-to-service communication"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Groups returned successfully")
    })
    public List<?> getGroupsForUsername(
            @Parameter(
                description = "Username of the member",
                example = "puneethaf",
                required = true
            )
            @PathVariable String username);
}
