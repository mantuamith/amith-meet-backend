package com.algomeet.subscription.api;

import com.algomeet.subscription.api.swagger.PlanComparisonControllerDoc;
import com.algomeet.subscription.dto.PlanComparisonResponse;
import com.algomeet.subscription.service.PlanComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/plans")
@RequiredArgsConstructor
public class PlanComparisonController implements PlanComparisonControllerDoc {

    private final PlanComparisonService service;

    @GetMapping("/comparison")
    public PlanComparisonResponse getComparison() {
        return service.getComparison();
    }
}
