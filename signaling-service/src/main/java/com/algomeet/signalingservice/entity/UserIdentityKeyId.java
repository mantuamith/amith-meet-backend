package com.algomeet.signalingservice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class UserIdentityKeyId implements Serializable {    
    private static final long serialVersionUID = 1L;

    @Column(name = "user_key", nullable = false, updatable = false)
	private UUID userKey;

	@Column(name = "identity_key", nullable = false, unique = true, length = 88)
    private String identityKey;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserIdentityKeyId)) return false;
        UserIdentityKeyId that = (UserIdentityKeyId) o;
        return Objects.equals(userKey, that.userKey) &&
               Objects.equals(identityKey, that.identityKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey, identityKey);
    }
}