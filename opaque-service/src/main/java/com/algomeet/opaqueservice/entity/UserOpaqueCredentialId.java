package com.algomeet.opaqueservice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import com.algomeet.opaqueservice.enums.CredentialType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Data;

@Data
@Embeddable
public class UserOpaqueCredentialId implements Serializable {

    private static final long serialVersionUID = 1L;

	@Column(name = "user_key", nullable = false)
    private UUID userKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false)
    private CredentialType type;

    public UserOpaqueCredentialId() {}

    public UserOpaqueCredentialId(UUID userKey, CredentialType type) {
        this.userKey = userKey;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserOpaqueCredentialId)) return false;
        UserOpaqueCredentialId that = (UserOpaqueCredentialId) o;
        return Objects.equals(userKey, that.userKey) &&
               type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey, type);
    }
}