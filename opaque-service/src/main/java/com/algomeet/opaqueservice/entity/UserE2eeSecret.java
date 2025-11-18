package com.algomeet.opaqueservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "user_e2ee_secrets")
public class UserE2eeSecret {
    @EmbeddedId
    private UserE2eeSecretId id;

    @Lob
    @Column(name = "secret_key", nullable = false, length = 255)
    private String secretKey;

    public UserE2eeSecret() {}

    public UserE2eeSecret(UserE2eeSecretId id, String secretKey) {
        this.id = id;
        this.secretKey = secretKey;
    }
}