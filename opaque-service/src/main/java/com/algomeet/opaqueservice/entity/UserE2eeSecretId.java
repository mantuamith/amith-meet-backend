package com.algomeet.opaqueservice.entity;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import com.algomeet.opaqueservice.enums.CredentialType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class UserE2eeSecretId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name = "user_key", nullable = false)
	private UUID userKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "credential_type", nullable = false)
	private CredentialType type;

	public UserE2eeSecretId() {}

	public UserE2eeSecretId(UUID userKey, CredentialType type) {
		this.userKey = userKey;
		this.type = type;
	}

	public UUID getUserKey() {
		return userKey;
	}

	public CredentialType getType() {
		return type;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof UserE2eeSecretId)) return false;
		UserE2eeSecretId that = (UserE2eeSecretId) o;
		return Objects.equals(userKey, that.userKey) &&
				type == that.type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(userKey, type);
	}
}
