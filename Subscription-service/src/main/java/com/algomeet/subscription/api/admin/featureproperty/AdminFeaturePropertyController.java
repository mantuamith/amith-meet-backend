package com.algomeet.subscription.api.admin.featureproperty;

import com.algomeet.subscription.api.swagger.featureproperty.AdminFeaturePropertyControllerDoc;
import com.algomeet.subscription.dto.admin.featureproperty.*;
import com.algomeet.subscription.service.admin.featureproperty.AdminFeaturePropertyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/feature-properties")
@RequiredArgsConstructor
public class AdminFeaturePropertyController implements AdminFeaturePropertyControllerDoc {

    private final AdminFeaturePropertyService service;

    @PostMapping
    public AdminFeaturePropertyResponse create(
            @RequestBody @Valid AdminFeaturePropertyCreateRequest request
    ) {
        return service.create(request);
    }

    @GetMapping("/by-feature/{featureId}")
    public List<AdminFeaturePropertyResponse> listByFeature(
            @PathVariable("featureId") UUID featureId
    ) {
        return service.listByFeature(featureId);
    }

    @PutMapping("/{id}")
    public AdminFeaturePropertyResponse update(
            @PathVariable("id") UUID id,
            @RequestBody @Valid AdminFeaturePropertyUpdateRequest request
    ) {
        return service.update(id, request);
    }
}
