package com.algomeet.subscription.repository;

import com.algomeet.subscription.SubscriptionServiceApplication;
import com.algomeet.subscription.entity.PlanFeatureValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = SubscriptionServiceApplication.class)
@ActiveProfiles("test")
class PlanFeatureValueRepositoryTest {

    @Autowired
    PlanFeatureValueRepository repository;

    @Test
    void shouldFetchAllForComparison() {

        List<PlanFeatureValue> values = repository.findAllForComparison();

        assertThat(values).isNotNull();
        assertThat(values).isNotEmpty();

        assertThat(values.get(0).getPlan()).isNotNull();
        assertThat(values.get(0).getFeatureProperty()).isNotNull();
        assertThat(values.get(0).getFeatureProperty().getFeature()).isNotNull();
    }
}
