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
public class UserAccountBackupId implements Serializable {    
    private static final long serialVersionUID = 1L;

    @Column(name = "user_key", nullable = false, updatable = false)
	private UUID userKey;

	@Column(name = "device_id", nullable = false, length = 88)
    private String deviceId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserAccountBackupId)) return false;
        UserAccountBackupId that = (UserAccountBackupId) o;
        return Objects.equals(userKey, that.userKey) &&
               Objects.equals(deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey, deviceId);
    }
}