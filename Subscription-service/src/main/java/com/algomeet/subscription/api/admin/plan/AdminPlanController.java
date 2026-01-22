package com.algomeet.subscription.api.admin.plan;

import com.algomeet.subscription.api.swagger.plan.AdminPlanControllerDoc;
import com.algomeet.subscription.dto.admin.plan.AdminPlanCreateRequest;
import com.algomeet.subscription.dto.admin.plan.AdminPlanResponse;
import com.algomeet.subscription.dto.admin.plan.AdminPlanUpdateRequest;
import com.algomeet.subscription.service.admin.plan.AdminPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController implements AdminPlanControllerDoc {

    private final AdminPlanService service;

    @PostMapping
    public AdminPlanResponse create(@Valid @RequestBody AdminPlanCreateRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<AdminPlanResponse> list() {
        return service.findAll();
    }

    @PatchMapping("/{id}/status")
    public void updateStatus(
            @PathVariable("id") UUID id,
            @RequestParam("active") boolean active
    ) {
        service.updateStatus(id, active);
    }

    @PutMapping("/{id}")
    public AdminPlanResponse update(
            @PathVariable("id") UUID id,
            @RequestBody AdminPlanUpdateRequest request
    ) {
        return service.update(id, request);
    }

}
