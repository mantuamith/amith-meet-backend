package com.algomeet.subscription.api.admin.plan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdminPlanControllerIT {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldCreateAndFetchPlan() {

        var request = """
            {
              "code": "STARTER",
              "name": "Starter",
              "active": true
            }
        """;

        ResponseEntity<String> create =
                restTemplate.postForEntity(
                        "/api/subscription/admin/plans",
                        request,
                        String.class
                );

        assertThat(create.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<String> list =
                restTemplate.getForEntity(
                        "/api/subscription/admin/plans",
                        String.class
                );

        assertThat(list.getBody()).contains("STARTER");
    }
}
