package com.algomeet.signalservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "signal_user_sessions",
indexes = {
		@Index(name = "idx_user_session", columnList = "userKey, deviceId")
})
public class UserSession {
	@Id
	private Long sessionId;

	private UUID userKey;

	private Integer deviceId;

	private UUID peerUserKey;

	private Integer peerDeviceId;

	private String sessionState;

	private Instant updatedAt;
}
