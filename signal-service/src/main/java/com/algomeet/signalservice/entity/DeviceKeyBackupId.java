package com.algomeet.signalservice.entity;

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
public class DeviceKeyBackupId implements Serializable {    
    private static final long serialVersionUID = 1L;

	private UUID userKey;

    private Integer deviceId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceKeyBackupId)) return false;
        DeviceKeyBackupId that = (DeviceKeyBackupId) o;
        return Objects.equals(userKey, that.userKey) &&
               Objects.equals(deviceId, that.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey, deviceId);
    }
}