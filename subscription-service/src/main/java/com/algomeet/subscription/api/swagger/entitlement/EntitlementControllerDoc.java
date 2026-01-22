package com.algomeet.subscription.api.swagger.entitlement;

import com.algomeet.subscription.dto.entitlement.EntitlementCheckRequest;
import com.algomeet.subscription.dto.entitlement.EntitlementCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Entitlements (Internal)",
    description = """
        Internal API for checking subscription entitlements.

        This API answers the question:
        "Given a plan, is a feature/property allowed?"

        Intended consumers:
        • Auth-service
        • User-service
        • Feature-gated backend services
        • API Gateway / BFF layer

        ⚠️ Not for direct frontend usage.
        """
)
public interface EntitlementControllerDoc {

    // ==================================================
    // ENTITLEMENT CHECK
    // ==================================================

    @Operation(
        summary = "Check plan entitlements",
        description = """
            Evaluates whether a subscription plan is entitled
            to use specific features or properties.

            The caller provides:
            • planCode (e.g. FREE, PRO, ENTERPRISE)
            • a list of feature/property checks

            The response returns:
            • Whether each item is allowed
            • The resolved value (if applicable)

            -------------------------
            🔹 Typical Use Cases
            -------------------------
            • Allow / deny feature access
            • Enforce limits (e.g. max participants)
            • Enable or disable UI actions
            • Backend authorization checks

            -------------------------
            🔹 Example Scenario
            -------------------------
            A service wants to check if a user on PRO plan can:
            - Create meetings
            - Host meetings longer than 30 minutes

            It sends:
            planCode = "PRO"
            checks = [
              { feature: "MEETING", property: "CREATE" },
              { feature: "MEETING", property: "MAX_DURATION" }
            ]

            -------------------------
            🔹 Important Notes
            -------------------------
            • Plan must exist and be active
            • Feature + property must be defined
            • Missing values are treated as NOT ENTITLED
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Entitlement evaluation completed",
        content = @Content(
            schema = @Schema(implementation = EntitlementCheckResponse.class)
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request (unknown plan / feature / property)"
    )
    @ApiResponse(
        responseCode = "500",
        description = "Server error during entitlement evaluation"
    )
    EntitlementCheckResponse check(
        @Schema(description = "Entitlement check request")
        EntitlementCheckRequest request
    );
}
