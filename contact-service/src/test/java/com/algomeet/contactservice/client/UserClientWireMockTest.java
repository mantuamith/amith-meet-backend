package com.algomeet.contactservice.client;

import com.algomeet.contactservice.dto.UserDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spins up a WireMock server on a random port and points Feign 'user-service' at it.
 * Verifies request path/query and response mapping for exact(...) call.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "feign.client.user-service.url=http://localhost:${wiremock.server.port}",
                "spring.flyway.enabled=false",
                "spring.liquibase.enabled=false"
        }
)
@AutoConfigureWireMock(port = 0)
class UserClientWireMockTest {

    @Autowired
    private UserClient userClient;

    @Test
    void exact_returnsMappedUserDto() {
        stubFor(get(urlPathEqualTo("/internal/users/lookup/exact"))
                .withQueryParam("q", equalTo("alice"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                              "id": 42,
                              "username": "alice",
                              "email": "alice@example.com",
                              "userKey": "11111111-2222-3333-4444-555555555555",
                              "enabled": true
                            }
                        """)
                        .withStatus(200)));

        UserDto dto = userClient.exact("alice");

        assertThat(dto).isNotNull();
        assertThat(dto.getUsername()).isEqualTo("alice");
        assertThat(dto.getEmail()).isEqualTo("alice@example.com");
        assertThat(dto.getUserKey()).isEqualTo("11111111-2222-3333-4444-555555555555");

        verify(getRequestedFor(urlPathEqualTo("/internal/users/lookup/exact"))
                .withQueryParam("q", equalTo("alice")));
    }

    @Test
    void exact_404_returnsNullOrThrowsDependingOnFeignConfig() {
        stubFor(get(urlPathEqualTo("/internal/users/lookup/exact"))
                .withQueryParam("q", equalTo("ghost"))
                .willReturn(aResponse().withStatus(404)));

        try {
            UserDto dto = userClient.exact("ghost");
            // If your Feign config maps 404 to null, assert null:
            assertThat(dto).isNull();
        } catch (Exception ex) {
            // If Feign throws for 404 (default), assert the exception type if you prefer.
            assertThat(ex).isInstanceOf(Exception.class);
        }
    }
}
