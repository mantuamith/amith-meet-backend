package com.algomeet.subscription.api;

import com.algomeet.subscription.config.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class PlanComparisonControllerIT  {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldReturnComparisonJson() {

        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        "/api/subscription/public/plans/comparison",
                        String.class
                );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("plans");
        assertThat(response.getBody()).contains("features");
        assertThat(response.getBody()).contains("MEETING_DURATION");
    }
}
