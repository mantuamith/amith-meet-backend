// otp/PendingRegistrationDoc.java
package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.DeviceType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("pending_registrations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PendingRegistrationDoc {

    @Id
    private String txn;

    private String username;
    private String email;
    private String phone;

    // store BCrypt hash already (no raw)
    private String passwordHash;

    private String deviceId;
    private DeviceType deviceType;

    private String country;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private Integer loginTypePolicy;

    private Instant createdAt;

    // auto-expire (match otp.ttlSeconds in config; keep small slack if desired)
    @Indexed(expireAfterSeconds = 900)  // will be overridden at runtime if you want; or keep constant
    private Instant expireAt;

    public PendingRegistrationDoc(
            String txn,
            String username,
            String email,
            String phone,

            String passwordHash,
            String deviceId,
            DeviceType deviceType,
            Instant createdAt,
            Instant expireAt
    ) {
        this.txn = txn;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.deviceId = deviceId;
        this.deviceType = deviceType;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
    }


}
