package com.algomeet.signalingservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

@AllArgsConstructor
@Data
public class UserSessionBackupId implements Serializable {
    private UUID userKey;
    private String sessionId;
}