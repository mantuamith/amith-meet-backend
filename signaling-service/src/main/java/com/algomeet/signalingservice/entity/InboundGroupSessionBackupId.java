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
public class InboundGroupSessionBackupId implements Serializable {
    
    private static final long serialVersionUID = 1L;

	@Column(nullable = false)
    private UUID userKey;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private int ratchetIndex;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InboundGroupSessionBackupId)) return false;
        InboundGroupSessionBackupId that = (InboundGroupSessionBackupId) o;
        return ratchetIndex == that.ratchetIndex &&
               Objects.equals(userKey, that.userKey) &&
               Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey, sessionId, ratchetIndex);
    }
}