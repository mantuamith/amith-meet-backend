package com.algomeet.subscription.service;

import com.algomeet.subscription.config.PostgresContainerConfig;
import com.algomeet.subscription.dto.PlanComparisonResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlanComparisonServiceTest  {

    @Autowired
    PlanComparisonService service;

    @Test
    void shouldBuildComparisonTable() {

        PlanComparisonResponse response = service.getComparison();

        assertThat(response).isNotNull();
        assertThat(response.plans()).isNotEmpty();
        assertThat(response.features()).isNotEmpty();

        var firstFeatureGroup = response.features().get(0);
        assertThat(firstFeatureGroup.items()).isNotEmpty();

        var firstItem = firstFeatureGroup.items().get(0);
        assertThat(firstItem.values()).isNotEmpty();
    }
}
