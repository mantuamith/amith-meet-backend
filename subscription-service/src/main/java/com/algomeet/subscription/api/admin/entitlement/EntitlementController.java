package com.algomeet.subscription.api.admin.entitlement;

import com.algomeet.subscription.api.swagger.entitlement.EntitlementControllerDoc;
import com.algomeet.subscription.dto.entitlement.EntitlementCheckRequest;
import com.algomeet.subscription.dto.entitlement.EntitlementCheckResponse;
import com.algomeet.subscription.service.admin.entitlement.EntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscription/internal/entitlements")
@RequiredArgsConstructor
public class EntitlementController implements EntitlementControllerDoc {

    private final EntitlementService service;

    @PostMapping("/check")
    public EntitlementCheckResponse check(
            @RequestBody EntitlementCheckRequest request
    ) {
        return service.check(request);
    }
}
