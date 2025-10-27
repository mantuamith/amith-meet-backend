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
public class OutboundGroupSessionBackupId implements Serializable {    
    private static final long serialVersionUID = 1L;

	@Column(nullable = false)
    private UUID userKey;

    @Column(nullable = false)
    private String sessionId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutboundGroupSessionBackupId)) return false;
        OutboundGroupSessionBackupId that = (OutboundGroupSessionBackupId) o;
        return Objects.equals(userKey, that.userKey) &&
               Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey, sessionId);
    }
}