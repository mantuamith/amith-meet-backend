package com.algomeet.signalingservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserSessionBackupId implements Serializable {
    private static final long serialVersionUID = 1L;
	private UUID userKey;
    private String sessionId;
}