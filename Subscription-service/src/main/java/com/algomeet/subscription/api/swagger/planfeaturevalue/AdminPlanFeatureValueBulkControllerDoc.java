package com.algomeet.subscription.api.swagger.admin.planfeaturevalue;

import com.algomeet.subscription.dto.admin.planfeaturevalue.AdminPlanFeatureValueBulkUpsertRequest;
import com.algomeet.subscription.dto.admin.planfeaturevalue.AdminPlanFeatureValueBulkUpsertResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@Tag(
        name = "Admin – Plan Feature Values (Bulk)",
        description = """
        Administrative API for BULK UPSERT of feature property values for a plan.

        This API is designed for:
        • Admin plan configuration screens
        • One-shot plan setup
        • Mass updates without multiple API calls

        Behavior:
        • If value exists → UPDATE
        • If value does not exist → CREATE
        • No deletion is performed

        ⚠️ ADMIN-ONLY API
        """
)
public interface AdminPlanFeatureValueBulkControllerDoc {

    // ==================================================
    // BULK UPSERT
    // ==================================================

    @Operation(
            summary = "Bulk upsert feature values for a plan",
            description = """
            Creates or updates multiple feature property values for a given plan
            in a single request.

            -------------------------
            🔹 What this API DOES
            -------------------------
            • Assigns values to multiple feature properties
            • Validates value types per property
            • Creates missing entries
            • Updates existing ones

            -------------------------
            🔹 What this API RETURNS
            -------------------------
            • Count of requested items
            • Count of created entries
            • Count of updated entries
            • Per-item result with action (CREATED / UPDATED)

            -------------------------
            🔹 What this API DOES NOT DO
            -------------------------
            • Does NOT delete missing values
            • Does NOT change feature or property definitions
            • Does NOT affect other plans

            -------------------------
            🔹 Typical Use Case
            -------------------------
            Admin opens "PRO Plan Configuration" screen,
            edits multiple values, clicks "Save".

            One API call persists everything.
            """
    )
    @ApiResponse(
            responseCode = "200",
            description = "Bulk upsert completed successfully",
            content = @Content(
                    schema = @Schema(
                            implementation = AdminPlanFeatureValueBulkUpsertResponse.class
                    ),
                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                            value = """
                {
                  "planId": "82c258be-861d-45d1-867a-13289d1e7ded",
                  "totalRequested": 3,
                  "created": 1,
                  "updated": 2,
                  "results": [
                    {
                      "featurePropertyId": "11111111-aaaa-bbbb-cccc-111111111111",
                      "planFeatureValueId": "aaaa-bbbb-cccc-dddd-1111",
                      "action": "UPDATED"
                    },
                    {
                      "featurePropertyId": "22222222-aaaa-bbbb-cccc-222222222222",
                      "planFeatureValueId": "aaaa-bbbb-cccc-dddd-2222",
                      "action": "CREATED"
                    },
                    {
                      "featurePropertyId": "33333333-aaaa-bbbb-cccc-333333333333",
                      "planFeatureValueId": "aaaa-bbbb-cccc-dddd-3333",
                      "action": "UPDATED"
                    }
                  ]
                }
                """
                    )
            )
    )
    @ApiResponse(responseCode = "400", description = "Invalid value type or malformed request")
    @ApiResponse(responseCode = "404", description = "Plan or FeatureProperty not found")
    AdminPlanFeatureValueBulkUpsertResponse bulkUpsert(
            @PathVariable("planId")
            @Schema(
                    description = "Plan UUID",
                    example = "82c258be-861d-45d1-867a-13289d1e7ded"
            )
            UUID planId,

            @RequestBody
            @Valid
            @Schema(
                    description = "Bulk feature value payload",
                    example = """
            {
              "values": [
                {
                  "featurePropertyId": "11111111-aaaa-bbbb-cccc-111111111111",
                  "value": "true"
                },
                {
                  "featurePropertyId": "22222222-aaaa-bbbb-cccc-222222222222",
                  "value": "100"
                },
                {
                  "featurePropertyId": "33333333-aaaa-bbbb-cccc-333333333333",
                  "value": "30"
                }
              ]
            }
            """
            )
            AdminPlanFeatureValueBulkUpsertRequest request
    );
}
