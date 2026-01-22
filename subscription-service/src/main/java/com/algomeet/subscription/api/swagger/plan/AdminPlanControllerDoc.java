package com.algomeet.subscription.api.swagger.plan;

import com.algomeet.subscription.dto.admin.plan.AdminPlanCreateRequest;
import com.algomeet.subscription.dto.admin.plan.AdminPlanResponse;
import com.algomeet.subscription.dto.admin.plan.AdminPlanUpdateRequest;
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
    name = "Admin – Plans",
    description = """
        Administrative APIs for managing PLANS.

        A Plan represents a bundle of entitlements.
        Examples:
        • BASIC
        • PRO
        • BUSINESS
        • ENTERPRISE

        Plans:
        • Are referenced by users (via planCode)
        • Do not store feature values directly
        • Are linked to Feature Properties via PlanFeatureValue

        ⚠️ ADMIN-ONLY APIs
        """
)
public interface AdminPlanControllerDoc {

    // ==================================================
    // CREATE PLAN
    // ==================================================

    @Operation(
        summary = "Create plan",
        description = """
            Creates a new plan.

            A plan is an empty container at creation time.
            Feature values will be assigned later.

            -------------------------
            🔹 Example
            -------------------------
            code  = "PRO"
            name  = "Professional"
            active = true
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Plan created successfully",
        content = @Content(
            schema = @Schema(implementation = AdminPlanResponse.class)
        )
    )
    @ApiResponse(responseCode = "400", description = "Invalid input or duplicate plan code")
    AdminPlanResponse create(
        @RequestBody
        @Valid
        @Schema(
            description = "Plan creation payload",
            example = """
            {
              "code": "PRO",
              "name": "Professional",
              "active": true
            }
            """
        )
        AdminPlanCreateRequest request
    );

    // ==================================================
    // LIST PLANS
    // ==================================================

    @Operation(
        summary = "List all plans",
        description = """
            Returns all plans in the system.

            Typically used:
            • Admin dashboards
            • Plan configuration screens
            • Internal services (entitlement mapping)

            Includes both active and inactive plans.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "List of plans",
        content = @Content(
            schema = @Schema(implementation = AdminPlanResponse.class)
        )
    )
    List<AdminPlanResponse> list();

    // ==================================================
    // UPDATE PLAN STATUS
    // ==================================================

    @Operation(
        summary = "Enable or disable plan",
        description = """
            Toggles plan availability.

            • active = true  → plan can be assigned to users
            • active = false → plan is hidden / deprecated

            ⚠️ Does NOT delete the plan.
            Existing users remain unaffected unless enforced elsewhere.
            """
    )
    @ApiResponse(responseCode = "200", description = "Plan status updated")
    @ApiResponse(responseCode = "404", description = "Plan not found")
    void updateStatus(
        @PathVariable("id")
        @Schema(
            description = "Plan UUID",
            example = "82c258be-861d-45d1-867a-13289d1e7ded"
        )
        UUID id,

        @RequestParam("active")
        @Schema(
            description = "Whether the plan is active",
            example = "false"
        )
        boolean active
    );

    // ==================================================
    // UPDATE PLAN
    // ==================================================

    @Operation(
        summary = "Update plan",
        description = """
            Updates plan metadata.

            Allowed updates:
            • Display name
            • Active flag

            ⚠️ Plan code is immutable once created.
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Plan updated successfully",
        content = @Content(
            schema = @Schema(implementation = AdminPlanResponse.class)
        )
    )
    @ApiResponse(responseCode = "404", description = "Plan not found")
    AdminPlanResponse update(
        @PathVariable("id")
        @Schema(
            description = "Plan UUID",
            example = "82c258be-861d-45d1-867a-13289d1e7ded"
        )
        UUID id,

        @RequestBody
        @Valid
        @Schema(
            description = "Plan update payload",
            example = """
            {
              "name": "Business Plus",
              "active": true
            }
            """
        )
        AdminPlanUpdateRequest request
    );
}
