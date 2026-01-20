package com.algomeet.subscription.config;

import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresContainerConfig {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("subscription_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        POSTGRES.start();

        System.setProperty("spring.datasource.url", POSTGRES.getJdbcUrl());
        System.setProperty("spring.datasource.username", POSTGRES.getUsername());
        System.setProperty("spring.datasource.password", POSTGRES.getPassword());
    }
}
