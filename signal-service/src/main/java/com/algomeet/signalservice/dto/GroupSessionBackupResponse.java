package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;

@Data
public class GroupSessionBackupResponse {
    private UUID groupId;
    private UUID distributionId;
    private Integer deviceId;
    /** true = inbound, false = outbound */
    private boolean inbound;
    
    private UUID senderUserKey;
    private Integer senderDeviceId;
    private String serializedSession;
    private String aesAlg;
    private String version;
    private String salt;
    private Instant createdAt;
    private Instant updatedAt;
}