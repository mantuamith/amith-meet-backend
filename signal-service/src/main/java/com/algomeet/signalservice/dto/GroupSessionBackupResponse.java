package com.algomeet.signalservice.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Data;

@Data
public class GroupSessionBackupResponse {
    private String groupId;
    private UUID distributionId;
    private Integer deviceId;
    private UUID remoteUserKey;
    private Integer remoteDeviceId;
    private String serializedSenderKey;
    private String aesAlg;
    private String version;
    private String salt;
    private Instant createdAt;
    private Instant updatedAt;
}