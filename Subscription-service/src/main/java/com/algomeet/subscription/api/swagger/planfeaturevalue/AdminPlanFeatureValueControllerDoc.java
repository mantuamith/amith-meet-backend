package com.algomeet.subscription.api.swagger.planfeaturevalue;

import com.algomeet.subscription.dto.admin.planfeaturevalue.AdminPlanFeatureValueCreateRequest;
import com.algomeet.subscription.dto.admin.planfeaturevalue.AdminPlanFeatureValueResponse;
import com.algomeet.subscription.dto.admin.planfeaturevalue.AdminPlanFeatureValueUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@Tag(
    name = "Admin – Plan Feature Values",
    description = """
        Administrative APIs for assigning FEATURE PROPERTY VALUES to PLANS.

        This is the CORE entitlement configuration layer.

        Relationship:
        Plan ──┬── Feature
              └── FeatureProperty ── value

        Example:
        Plan = PRO
        Feature = MEETING
        Property = MAX_PARTICIPANTS
        Value = 100

        ⚠️ ADMIN-ONLY APIs
        """
)
public interface AdminPlanFeatureValueControllerDoc {

    // ==================================================
    // CREATE VALUE
    // ==================================================

    @Operation(
        summary = "Assign feature property value to plan",
        description = """
            Assigns a value to a feature property for a specific plan.

            Each (planId + featurePropertyId) pair is UNIQUE.

            -------------------------
            🔹 Example Use Case
            -------------------------
            Assign max participants for PRO plan:

            Plan          : PRO
            Feature       : MEETING
            Property      : MAX_PARTICIPANTS
            Value         : "100"
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Feature value assigned successfully",
        content = @Content(
            schema = @Schema(implementation = AdminPlanFeatureValueResponse.class)
        )
    )
    @ApiResponse(responseCode = "400", description = "Duplicate value or invalid value type")
    @ApiResponse(responseCode = "404", description = "Plan or FeatureProperty not found")
    AdminPlanFeatureValueResponse create(
        @RequestBody
        @Valid
        @Schema(
            description = "Plan feature value creation payload",
            example = """
            {
              "planId": "82c258be-861d-45d1-867a-13289d1e7ded",
              "featurePropertyId": "9a3d0a6c-1f42-4f0b-9b6a-4fbd9c8c6b11",
              "value": "100"
            }
            """
        )
        AdminPlanFeatureValueCreateRequest request
    );

    // ==================================================
    // UPDATE VALUE
    // ==================================================

    @Operation(
        summary = "Update feature property value",
        description = """
            Updates the value of an existing plan-feature-property mapping.

            -------------------------
            🔹 Example Use Case
            -------------------------
            Increase PRO plan participant limit:

            Old value : 100
            New value : 150
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Feature value updated successfully",
        content = @Content(
            schema = @Schema(implementation = AdminPlanFeatureValueResponse.class)
        )
    )
    @ApiResponse(responseCode = "400", description = "Invalid value type")
    @ApiResponse(responseCode = "404", description = "Plan feature value not found")
    AdminPlanFeatureValueResponse update(
        @PathVariable("id")
        @Schema(
            description = "PlanFeatureValue UUID",
            example = "b19f7b24-6a2c-4c34-9a48-5b97e9b6d9f1"
        )
        UUID id,

        @RequestBody
        @Valid
        @Schema(
            description = "Updated value payload",
            example = """
            {
              "value": "150"
            }
            """
        )
        AdminPlanFeatureValueUpdateRequest request
    );

    // ==================================================
    // LIST VALUES BY PLAN
    // ==================================================

    @Operation(
        summary = "List feature values by plan",
        description = """
            Returns all feature property values assigned to a plan.

            This API is typically used for:
            • Admin plan configuration UI
            • Bulk edit screens
            • Internal validation

            Results are ordered by:
            • Feature display order
            • Property label
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of plan feature values",
        content = @Content(
            schema = @Schema(implementation = AdminPlanFeatureValueResponse.class)
        )
    )
    List<AdminPlanFeatureValueResponse> listByPlan(
        @RequestParam("planId")
        @Schema(
            description = "Plan UUID",
            example = "82c258be-861d-45d1-867a-13289d1e7ded"
        )
        UUID planId
    );

    // ==================================================
    // DELETE VALUE
    // ==================================================

    @Operation(
        summary = "Delete feature property value",
        description = """
            Deletes a feature property value from a plan.

            -------------------------
            🔹 Example Use Case
            -------------------------
            Remove recording feature from BASIC plan.
            """
    )
    @ApiResponse(responseCode = "200", description = "Feature value deleted successfully")
    @ApiResponse(responseCode = "404", description = "Plan feature value not found")
    void delete(
        @PathVariable("id")
        @Schema(
            description = "PlanFeatureValue UUID",
            example = "b19f7b24-6a2c-4c34-9a48-5b97e9b6d9f1"
        )
        UUID id
    );
}
