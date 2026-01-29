package com.algomeet.subscription.api.admin.feature;

import com.algomeet.subscription.api.swagger.feature.AdminFeatureControllerDoc;
import com.algomeet.subscription.dto.admin.feature.*;
import com.algomeet.subscription.service.admin.feature.AdminFeatureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscription/admin/features")
@RequiredArgsConstructor
public class AdminFeatureController implements AdminFeatureControllerDoc {

    private final AdminFeatureService service;

    @PostMapping
    public AdminFeatureResponse create(
            @RequestBody @Valid AdminFeatureCreateRequest request
    ) {
        return service.create(request);
    }

    @GetMapping
    public List<AdminFeatureResponse> list() {
        return service.findAll();
    }

    @PutMapping("/{id}")
    public AdminFeatureResponse update(
            @PathVariable("id") UUID id,
            @RequestBody @Valid AdminFeatureUpdateRequest request
    ) {
        return service.update(id, request);
    }
}
