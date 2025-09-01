// PendingPasswordResetDoc.java
package com.algomeet.authservice.otp;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("pending_password_resets")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class PendingPasswordResetDoc {
  @Id
  private String id;       // UUID
  private String login;    // normalized email or phone (preferred: email)
  private String channel;  // EMAIL | PHONE
  private Instant createdAt;
  private Instant expireAt;
}
