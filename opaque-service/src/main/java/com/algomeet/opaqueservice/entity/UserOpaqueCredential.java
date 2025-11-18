package com.algomeet.opaqueservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "user_opaque_credentials")
public class UserOpaqueCredential {
    @EmbeddedId
    private UserOpaqueCredentialId id;

    @Lob
    @Column(name = "rec", nullable = false, length = 255)
    private String rec;

    public UserOpaqueCredential() {}

    public UserOpaqueCredential(UserOpaqueCredentialId id, String rec) {
        this.id = id;
        this.rec = rec;
    }
}
