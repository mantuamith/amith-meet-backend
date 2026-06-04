package com.algomeet.userservice.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link MediaServiceClient}.
 *
 * Uses {@link MockRestServiceServer} so no Mockito inline-mocking of RestTemplate
 * is required — fully compatible with JDK 23.
 */
class MediaServiceClientTest {

    private static final String    BASE_URL = "http://localhost:8095";
    private static final UUID      USER_KEY = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String    EXPECTED_URL =
            BASE_URL + "/internal/media/users/" + USER_KEY + "/storage-usage";

    private RestTemplate           restTemplate;
    private MockRestServiceServer  server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server       = MockRestServiceServer.createServer(restTemplate);
    }

    private MediaServiceClient enabled() {
        return new MediaServiceClient(restTemplate, BASE_URL, true);
    }

    private MediaServiceClient disabled() {
        return new MediaServiceClient(restTemplate, BASE_URL, false);
    }

    // ── 1. Happy path ─────────────────────────────────────────────────────────

    @Test
    void deleteStorageUsage_success_callsCorrectDeleteEndpoint() {
        server.expect(requestTo(EXPECTED_URL))
              .andExpect(method(HttpMethod.DELETE))
              .andRespond(withSuccess());

        enabled().deleteStorageUsage(USER_KEY);

        server.verify(); // asserts the DELETE was actually made
    }

    // ── 2. 404 — no record existed ────────────────────────────────────────────

    @Test
    void deleteStorageUsage_404_isSilentlyIgnored() {
        // media-service returns 404 when there is no storage record — treat as no-op
        server.expect(requestTo(EXPECTED_URL))
              .andExpect(method(HttpMethod.DELETE))
              .andRespond(withResourceNotFound());

        assertDoesNotThrow(() -> enabled().deleteStorageUsage(USER_KEY));
        server.verify();
    }

    // ── 3. Server error ───────────────────────────────────────────────────────

    @Test
    void deleteStorageUsage_500_doesNotPropagateException() {
        // media-service returns 500 — must not abort account deletion
        server.expect(requestTo(EXPECTED_URL))
              .andExpect(method(HttpMethod.DELETE))
              .andRespond(withServerError());

        assertDoesNotThrow(() -> enabled().deleteStorageUsage(USER_KEY));
        server.verify();
    }

    // ── 4. Network error ──────────────────────────────────────────────────────

    @Test
    void deleteStorageUsage_networkError_doesNotPropagateException() {
        // Simulate connection refused by pointing at a port nothing listens on
        RestTemplate badTemplate = new RestTemplate();
        MediaServiceClient client =
                new MediaServiceClient(badTemplate, "http://127.0.0.1:19999", true);

        // No real HTTP call succeeds — must swallow the exception
        assertDoesNotThrow(() -> client.deleteStorageUsage(USER_KEY));
    }

    // ── 5. Integration flag disabled ──────────────────────────────────────────

    @Test
    void deleteStorageUsage_whenDisabled_noHttpCallMade() {
        // server has no expectations — any request would cause verify() to fail
        disabled().deleteStorageUsage(USER_KEY);

        server.verify(); // passes because zero requests were made
    }

    // ── 6. URL is correctly formed ────────────────────────────────────────────

    @Test
    void deleteStorageUsage_urlContainsUserKeyAndCorrectPath() {
        UUID specificKey = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String expectedUrl =
                BASE_URL + "/internal/media/users/" + specificKey + "/storage-usage";

        server.expect(requestTo(expectedUrl))
              .andExpect(method(HttpMethod.DELETE))
              .andRespond(withSuccess());

        enabled().deleteStorageUsage(specificKey);

        server.verify();
    }
}
