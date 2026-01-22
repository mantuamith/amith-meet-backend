package com.algomeet.subscription.api.swagger;

import com.algomeet.subscription.dto.PlanComparisonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Plans (Public)",
    description = """
        Public APIs related to subscription plans.

        These APIs are consumed by:
        • Web frontend (pricing page)
        • Mobile apps
        • Marketing / landing pages

        No authentication is required.
        """
)
public interface PlanComparisonControllerDoc {

    // ==================================================
    // PLAN COMPARISON
    // ==================================================

    @Operation(
        summary = "Get plan comparison",
        description = """
            Returns a comparison matrix of all active subscription plans
            along with their features and values.

            The response is structured in a frontend-friendly format:
            • List of plans
            • Feature groups (UI sections)
            • Feature items with per-plan values

            **Use cases**
            - Pricing / plans page
            - Feature comparison table
            - Upgrade / downgrade decision UI

            **Notes**
            - Only ACTIVE plans are returned
            - Ordering is controlled by feature display order
            - Values are resolved from PlanFeatureValue
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "Plan comparison returned successfully",
        content = @Content(
            schema = @Schema(implementation = PlanComparisonResponse.class)
        )
    )
    @ApiResponse(
        responseCode = "500",
        description = "Server error while building comparison"
    )
    PlanComparisonResponse getComparison();
}
