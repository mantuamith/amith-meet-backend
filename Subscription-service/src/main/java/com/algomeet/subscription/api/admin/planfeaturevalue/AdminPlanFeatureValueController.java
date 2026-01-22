package com.algomeet.subscription.api.admin.planfeaturevalue;

import com.algomeet.subscription.api.swagger.planfeaturevalue.AdminPlanFeatureValueControllerDoc;
import com.algomeet.subscription.dto.admin.planfeaturevalue.*;
import com.algomeet.subscription.service.admin.planfeaturevalue.AdminPlanFeatureValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/plan-feature-values")
@RequiredArgsConstructor
public class AdminPlanFeatureValueController implements AdminPlanFeatureValueControllerDoc {

    private final AdminPlanFeatureValueService service;

    /* CREATE */
    @PostMapping
    public AdminPlanFeatureValueResponse create(
            @RequestBody AdminPlanFeatureValueCreateRequest request
    ) {
        return service.create(request);
    }

    /* UPDATE */
    @PutMapping("/{id}")
    public AdminPlanFeatureValueResponse update(
            @PathVariable("id") UUID id,
            @RequestBody AdminPlanFeatureValueUpdateRequest request
    ) {
        return service.update(id, request);
    }

    /* LIST BY PLAN */
    @GetMapping
    public List<AdminPlanFeatureValueResponse> listByPlan(
            @RequestParam("planId") UUID planId
    ) {
        return service.listByPlan(planId);
    }

    /* DELETE */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") UUID id) {
        service.delete(id);
    }
}
