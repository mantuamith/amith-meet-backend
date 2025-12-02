package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;

@Data
public class GroupSenderKeyBackupResponse {
    private UUID userKey;
    private String groupId;
    private UUID distributionId;
    private String serializedSkdm;
    private String version;
    private String aesAlg;
    private String salt;
    private Instant createdAt;
    private Instant updatedAt;
}