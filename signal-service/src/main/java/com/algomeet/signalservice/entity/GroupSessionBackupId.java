package com.algomeet.signalservice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class GroupSessionBackupId implements Serializable {    
    private static final long serialVersionUID = 1L;
    private UUID userKey;

    private String groupId;
    
    private UUID distributionId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupSessionBackupId)) return false;
        GroupSessionBackupId that = (GroupSessionBackupId) o;
        return distributionId == that.distributionId &&
               Objects.equals(userKey, that.userKey) &&
               Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey, groupId, distributionId);
    }
}