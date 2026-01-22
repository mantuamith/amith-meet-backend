package com.algomeet.subscription.api.swagger.feature;

import com.algomeet.subscription.dto.admin.feature.AdminFeatureCreateRequest;
import com.algomeet.subscription.dto.admin.feature.AdminFeatureResponse;
import com.algomeet.subscription.dto.admin.feature.AdminFeatureUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@Tag(
    name = "Admin – Features",
    description = """
        Administrative APIs for managing FEATURES.

        A Feature represents a high-level capability area in the system.
        Examples:
        • MEETING
        • CHAT
        • RECORDING
        • STORAGE

        Features are:
        • Defined once
        • Reused across plans
        • Containers for feature properties

        ⚠️ ADMIN-ONLY APIs
        """
)
public interface AdminFeatureControllerDoc {

    // ==================================================
    // CREATE FEATURE
    // ==================================================

    @Operation(
        summary = "Create feature",
        description = """
            Creates a new feature.

            A feature is a logical capability group.
            Feature properties (limits, toggles) are attached later.

            -------------------------
            🔹 Example Use Case
            -------------------------
            Create a Meetings feature displayed first in UI.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Feature created successfully",
        content = @Content(
            schema = @Schema(implementation = AdminFeatureResponse.class)
        )
    )
    @ApiResponse(responseCode = "400", description = "Invalid input or duplicate feature code")
    AdminFeatureResponse create(
        @RequestBody
        @Valid
        @Schema(
            description = "Feature creation payload",
            example = """
            {
              "code": "MEETING",
              "name": "Meetings",
              "uiGroup": "Core",
              "displayOrder": 1
            }
            """
        )
        AdminFeatureCreateRequest request
    );

    // ==================================================
    // LIST FEATURES
    // ==================================================

    @Operation(
        summary = "List all features",
        description = """
            Returns all features in the system.

            Commonly used during:
            • Feature property configuration
            • Plan configuration
            • Admin dashboards

            Results are ordered by displayOrder.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of features",
        content = @Content(
            schema = @Schema(implementation = AdminFeatureResponse.class)
        )
    )
    List<AdminFeatureResponse> list();

    // ==================================================
    // UPDATE FEATURE
    // ==================================================

    @Operation(
        summary = "Update feature",
        description = """
            Updates an existing feature.

            Allowed updates:
            • Rename feature
            • Change UI group
            • Change display order

            ⚠️ Feature code is immutable.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Feature updated successfully",
        content = @Content(
            schema = @Schema(implementation = AdminFeatureResponse.class)
        )
    )
    @ApiResponse(responseCode = "404", description = "Feature not found")
    AdminFeatureResponse update(
        @PathVariable("id")
        @Schema(
            description = "Feature UUID",
            example = "a3f2c7b4-8d92-4c89-b1a1-1a2b3c4d5e6f"
        )
        UUID id,

        @RequestBody
        @Valid
        @Schema(
            description = "Feature update payload",
            example = """
            {
              "name": "Meetings & Webinars",
              "uiGroup": "Core",
              "displayOrder": 1
            }
            """
        )
        AdminFeatureUpdateRequest request
    );
}
