// PasswordResetService.java
package com.algomeet.authservice.service;

import com.algomeet.authservice.exception.ResetTicketExpiredException;
import com.algomeet.authservice.exception.ResetTicketInvalidException;
import com.algomeet.authservice.otp.PendingPasswordResetDoc;
import com.algomeet.authservice.otp.PendingPasswordResetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

  private final PendingPasswordResetRepository repo;

  // TODO: externalize 10 minutes into AuthProperties.otp.resetTicketTtlSeconds
  private static final long RESET_TICKET_TTL_SECONDS = 600;

  public String issueResetTicket(String login, String channel) {
    String id = UUID.randomUUID().toString();
    Instant now = Instant.now();
    var doc = PendingPasswordResetDoc.builder()
        .id(id)
        .login(login.trim().toLowerCase())
        .channel(channel)
        .createdAt(now)
        .expireAt(now.plusSeconds(RESET_TICKET_TTL_SECONDS))
        .build();
    repo.save(doc);
    return id;
  }

  public PendingPasswordResetDoc consumeResetTicketOrThrow(String id) {
    var doc = repo.findById(id)
        .orElseThrow(() -> new ResetTicketInvalidException("Invalid or expired password reset ticket"));

    if (Instant.now().isAfter(doc.getExpireAt())) {
      repo.deleteById(id);
      throw new ResetTicketExpiredException("Password reset ticket expired");
    }
    repo.deleteById(id); // one-time use
    return doc;
  }
}
