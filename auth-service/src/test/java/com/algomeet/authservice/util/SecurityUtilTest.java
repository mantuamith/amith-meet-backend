package com.algomeet.authservice.util;

import com.algomeet.authservice.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("getUserRole returns ADMIN when authority is 'ADMIN'")
  void getUserRole_returnsAdmin() {
    var auth = new UsernamePasswordAuthenticationToken(
            "alice", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
    );
    SecurityContextHolder.getContext().setAuthentication(auth);

    var role = SecurityUtil.getUserRole();
    assertThat(role).isEqualTo(UserRole.ROLE_ADMIN);
  }

  @Test
  @DisplayName("getUserRole returns USER when authority is 'USER'")
  void getUserRole_returnsUser() {
    var auth = new UsernamePasswordAuthenticationToken(
            "bob", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );
    SecurityContextHolder.getContext().setAuthentication(auth);

    var role = SecurityUtil.getUserRole();
    assertThat(role).isEqualTo(UserRole.ROLE_USER);
  }

  @Test
  @DisplayName("getUserRole returns null when no Authentication present")
  void getUserRole_nullWhenNoAuth() {
    SecurityContextHolder.clearContext();
    assertThat(SecurityUtil.getUserRole()).isNull();
  }

  @Test
  @DisplayName("getUserRole returns null when authorities empty")
  void getUserRole_nullWhenNoAuthorities() {
    var auth = new UsernamePasswordAuthenticationToken("charlie", "n/a", List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(SecurityUtil.getUserRole()).isNull();
  }

  @Test
  @DisplayName("getUserRole returns null when authority doesn't match enum (e.g. 'ROLE_ADMIN')")
  void getUserRole_nullWhenBadAuthority() {
    var auth = new UsernamePasswordAuthenticationToken(
            "dana", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADKIN"))
    );
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(SecurityUtil.getUserRole()).isNull(); // parsing fails → null
  }

  @Test
  @DisplayName("isAdminUser true only when ADMIN")
  void isAdminUser_behavior() {
    // ADMIN → true
    var adminAuth = new UsernamePasswordAuthenticationToken(
            "eva", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
    );
    SecurityContextHolder.getContext().setAuthentication(adminAuth);
    assertThat(SecurityUtil.isAdminUser()).isTrue();

    // USER → false
    var userAuth = new UsernamePasswordAuthenticationToken(
            "frank", "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );
    SecurityContextHolder.getContext().setAuthentication(userAuth);
    assertThat(SecurityUtil.isAdminUser()).isFalse();

    // No auth → false
    SecurityContextHolder.clearContext();
    assertThat(SecurityUtil.isAdminUser()).isFalse();

    // Bad authority string → false
    var badAuth = new UsernamePasswordAuthenticationToken(
            "greg", "n/a",
            List.of(new SimpleGrantedAuthority("ADMIN_ROLE"))
    );
    SecurityContextHolder.getContext().setAuthentication(badAuth);
    assertThat(SecurityUtil.isAdminUser()).isFalse();
  }
}
