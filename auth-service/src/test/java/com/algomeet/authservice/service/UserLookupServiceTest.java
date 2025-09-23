package com.algomeet.authservice.service;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.dto.UserResponse;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

  @Mock UserClient userClient;
  @InjectMocks UserLookupService svc;

  @Test
  void byEmail() {
    var u = new UserResponse();
    u.setEmail("a@x.com");
    // Service always calls getUserByEmail(key)
    when(userClient.getUserByEmail("a@x.com")).thenReturn(u);

    assertThat(svc.findByLoginOr404("a@x.com")).isEqualTo(u);
  }

  @Test
  void byUsername() {
    var u = new UserResponse(); u.setUsername("alice");
    // Still stub getUserByEmail because the service uses it for any login
    when(userClient.getUserByEmail("alice")).thenReturn(u);

    assertThat(svc.findByLoginOr404("alice")).isEqualTo(u);
  }

  @Test
  void byPhone() {
    var u = new UserResponse(); u.setPhone("15551234");
    // Same here
    when(userClient.getUserByEmail("15551234")).thenReturn(u);

    assertThat(svc.findByLoginOr404("15551234")).isEqualTo(u);
  }

  @Test
  void notFound_throws() {
    var req = Request.create(
            Request.HttpMethod.GET, "/users/login/missing", Map.of(), null, StandardCharsets.UTF_8, null);

    // Stub ONLY the method the service calls
    when(userClient.getUserByEmail("missing"))
            .thenThrow(new FeignException.NotFound("nf", req, null, null));

    assertThatThrownBy(() -> svc.findByLoginOr404("missing"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
}
