package com.algomeet.authservice.service;

import com.algomeet.authservice.exception.ResetTicketExpiredException;
import com.algomeet.authservice.exception.ResetTicketInvalidException;
import com.algomeet.authservice.otp.PendingPasswordResetDoc;
import com.algomeet.authservice.otp.PendingPasswordResetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

  @Mock
  PendingPasswordResetRepository repo;

  @InjectMocks
  PasswordResetService svc;

  @Test
  void issueResetTicket_savesDoc_andReturnsId() {
    ArgumentCaptor<PendingPasswordResetDoc> cap = ArgumentCaptor.forClass(PendingPasswordResetDoc.class);

    Instant before = Instant.now();
    String id = svc.issueResetTicket("Alice@X.com", "EMAIL");
    Instant after = Instant.now();

    verify(repo).save(cap.capture());
    PendingPasswordResetDoc saved = cap.getValue();

    // returned id == saved doc id
    assertThat(id).isNotBlank();
    assertThat(saved.getId()).isEqualTo(id);

    // login normalized, channel set
    assertThat(saved.getLogin()).isEqualTo("alice@x.com");
    assertThat(saved.getChannel()).isEqualTo("EMAIL");

    // timestamps sensible
    assertThat(saved.getCreatedAt()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    assertThat(saved.getExpireAt()).isAfter(saved.getCreatedAt());

    // TTL ≈ 600s (allow a few seconds of jitter)
    long ttl = Duration.between(saved.getCreatedAt(), saved.getExpireAt()).getSeconds();
    assertThat(ttl).isBetween(590L, 610L);
  }

  @Test
  void consumeResetTicket_valid_returnsDoc_andDeletes() {
    var doc = PendingPasswordResetDoc.builder()
            .id("t-1")
            .login("user@x.com")
            .channel("EMAIL")
            .createdAt(Instant.now())
            .expireAt(Instant.now().plusSeconds(300))
            .build();

    when(repo.findById("t-1")).thenReturn(Optional.of(doc));

    var out = svc.consumeResetTicketOrThrow("t-1");

    assertThat(out).isSameAs(doc);
    verify(repo).deleteById("t-1"); // one-time use
  }

  @Test
  void consumeResetTicket_expired_throws_andDeletes() {
    var doc = PendingPasswordResetDoc.builder()
            .id("t-expired")
            .login("user@x.com")
            .channel("EMAIL")
            .createdAt(Instant.now().minusSeconds(1200))
            .expireAt(Instant.now().minusSeconds(1)) // already expired
            .build();

    when(repo.findById("t-expired")).thenReturn(Optional.of(doc));

    assertThatThrownBy(() -> svc.consumeResetTicketOrThrow("t-expired"))
            .isInstanceOf(ResetTicketExpiredException.class)
            .hasMessageContaining("expired");

    verify(repo).deleteById("t-expired"); // cleaned up on expiry
  }

  @Test
  void consumeResetTicket_missing_throws_invalid_andDoesNotDelete() {
    when(repo.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> svc.consumeResetTicketOrThrow("missing"))
            .isInstanceOf(ResetTicketInvalidException.class)
            .hasMessageContaining("Invalid");

    verify(repo, never()).deleteById(any());
  }
}
