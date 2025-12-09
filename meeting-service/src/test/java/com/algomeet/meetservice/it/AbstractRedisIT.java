package com.algomeet.meetservice.it;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.Assumptions;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
/**
 * Set -Dit.docker=false to skip Redis-container tests locally/CI.
 * Example: mvn -Dit.docker=false test
 */
@DisabledIfSystemProperty(named = "it.docker", matches = "(?i)false|no|0")
public abstract class AbstractRedisIT {

  private static GenericContainer<?> redis;

  private static boolean dockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  @BeforeAll
  void startRedis() {
    // If Docker isn't available, skip tests that need this base.
    Assumptions.assumeTrue(dockerAvailable(), "Docker not available, skipping Redis container tests");

    if (redis == null) {
      redis = new GenericContainer<>(DockerImageName.parse("redis:7.2.5"))
              .withExposedPorts(6379);
      redis.start();
    }
  }

  @AfterAll
  void stopRedis() {
    if (redis != null) {
      try { redis.stop(); } catch (Throwable ignore) {}
      redis = null;
    }
  }

  @DynamicPropertySource
  static void redisProps(DynamicPropertyRegistry r) {
    // Suppliers are evaluated lazily; will only be called if the context needs these props.
    r.add("spring.data.redis.host", () ->
            (redis != null && redis.isRunning()) ? redis.getHost() : "localhost");
    r.add("spring.data.redis.port", () ->
            (redis != null && redis.isRunning()) ? redis.getMappedPort(6379) : 6379);
  }
}
