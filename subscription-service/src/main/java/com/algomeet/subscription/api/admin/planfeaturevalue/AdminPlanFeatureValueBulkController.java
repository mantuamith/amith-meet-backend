package com.algomeet.subscription.api.admin.planfeaturevalue;

import com.algomeet.subscription.api.swagger.admin.planfeaturevalue.AdminPlanFeatureValueBulkControllerDoc;
import com.algomeet.subscription.dto.admin.planfeaturevalue.AdminPlanFeatureValueBulkUpsertRequest;
import com.algomeet.subscription.dto.admin.planfeaturevalue.AdminPlanFeatureValueBulkUpsertResponse;
import com.algomeet.subscription.service.admin.planfeaturevalue.AdminPlanFeatureValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/subscription/admin/plans")
@RequiredArgsConstructor
public class AdminPlanFeatureValueBulkController implements AdminPlanFeatureValueBulkControllerDoc {

    private final AdminPlanFeatureValueService service;

    @PutMapping("/{planId}/feature-values")
    public AdminPlanFeatureValueBulkUpsertResponse bulkUpsert(
            @PathVariable UUID planId,
            @RequestBody AdminPlanFeatureValueBulkUpsertRequest request
    ) {
        return service.bulkUpsert(planId, request);
    }
}
