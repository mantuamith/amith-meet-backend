package com.algomeet.subscription.api.swagger.featureproperty;

import com.algomeet.subscription.dto.admin.featureproperty.AdminFeaturePropertyCreateRequest;
import com.algomeet.subscription.dto.admin.featureproperty.AdminFeaturePropertyResponse;
import com.algomeet.subscription.dto.admin.featureproperty.AdminFeaturePropertyUpdateRequest;
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
    name = "Admin – Feature Properties",
    description = """
        Administrative APIs for managing FEATURE PROPERTIES.

        A Feature Property defines *what aspect* of a feature can vary by plan.

        Examples:
        • MAX_PARTICIPANTS
        • MEETING_DURATION
        • RECORDING_ENABLED

        Important:
        • Properties define structure only
        • Actual values are stored per-plan via PlanFeatureValue
        • Properties are reusable across all plans

        ⚠️ ADMIN-ONLY APIs
        """
)
public interface AdminFeaturePropertyControllerDoc {

    // ==================================================
    // CREATE FEATURE PROPERTY
    // ==================================================

    @Operation(
        summary = "Create feature property",
        description = """
            Creates a new property under a feature.

            A property defines:
            • What can vary across plans
            • How it should be interpreted (valueType)

            -------------------------
            🔹 Example Use Case
            -------------------------
            Feature: MEETING
            Property: MAX_PARTICIPANTS
            Value type: NUMBER
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Feature property created successfully",
        content = @Content(
            schema = @Schema(implementation = AdminFeaturePropertyResponse.class)
        )
    )
    @ApiResponse(responseCode = "400", description = "Invalid input or duplicate property key")
    AdminFeaturePropertyResponse create(
        @RequestBody
        @Valid
        @Schema(
            description = "Feature property creation payload",
            example = """
            {
              "featureId": "b4b3f1a2-9c31-4d8e-8e91-11aabbccdd22",
              "propKey": "MAX_PARTICIPANTS",
              "label": "Maximum participants",
              "valueType": "NUMBER"
            }
            """
        )
        AdminFeaturePropertyCreateRequest request
    );

    // ==================================================
    // LIST PROPERTIES BY FEATURE
    // ==================================================

    @Operation(
        summary = "List properties by feature",
        description = """
            Returns all properties belonging to a feature.

            Commonly used:
            • While configuring plan feature values
            • In admin configuration screens

            Properties are ordered for consistent UI display.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of feature properties",
        content = @Content(
            schema = @Schema(implementation = AdminFeaturePropertyResponse.class)
        )
    )
    List<AdminFeaturePropertyResponse> listByFeature(
        @PathVariable("featureId")
        @Schema(
            description = "Feature UUID",
            example = "b4b3f1a2-9c31-4d8e-8e91-11aabbccdd22"
        )
        UUID featureId
    );

    // ==================================================
    // UPDATE FEATURE PROPERTY
    // ==================================================

    @Operation(
        summary = "Update feature property",
        description = """
            Updates an existing feature property.

            Allowed updates:
            • Display label
            • Value type

            ⚠️ Property key is immutable.
            ⚠️ Changing valueType may require plan value updates.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Feature property updated successfully",
        content = @Content(
            schema = @Schema(implementation = AdminFeaturePropertyResponse.class)
        )
    )
    @ApiResponse(responseCode = "404", description = "Feature property not found")
    AdminFeaturePropertyResponse update(
        @PathVariable("id")
        @Schema(
            description = "Feature property UUID",
            example = "d9c8b7a6-1234-4f5e-9abc-ffeeddccbbaa"
        )
        UUID id,

        @RequestBody
        @Valid
        @Schema(
            description = "Feature property update payload",
            example = """
            {
              "label": "Maximum meeting participants",
              "valueType": "NUMBER"
            }
            """
        )
        AdminFeaturePropertyUpdateRequest request
    );
}
