package com.algomeet.authservice.otp;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
@Document(collection = "otps")
@CompoundIndexes({
    @CompoundIndex(name = "otp_recipient_purpose_created_idx",
            def = "{'recipient': 1, 'purpose': 1, 'createdAt': -1}")
})
public class Otp {
    @Id private String id;

    private String recipient;      // email or phone
    private String channel;        // "EMAIL" | "PHONE"
    private String purpose;        // e.g. "LOGIN"

    private String codeHash;       // BCrypt(otp + pepper)
    private int attempts;

    private Instant createdAt;

    @Indexed(name = "otp_expire_idx", expireAfterSeconds = 0)
    private Instant expiresAt;     // TTL index auto-cleans documents
}
