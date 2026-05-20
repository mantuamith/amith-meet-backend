package com.algomeet.signalservice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Embeddable
@Data
public class GroupSenderKeyBackupId implements Serializable {
	private static final long serialVersionUID = 1L;

	private UUID userKey;	    
	private UUID groupId;
	private UUID distributionId;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		GroupSenderKeyBackupId that = (GroupSenderKeyBackupId) o;
		return Objects.equals(userKey, that.userKey)
				&& Objects.equals(groupId, that.groupId)
				&& Objects.equals(distributionId, that.distributionId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userKey, groupId, distributionId);
	}
}