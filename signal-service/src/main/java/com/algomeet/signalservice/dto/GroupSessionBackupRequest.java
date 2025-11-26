package com.algomeet.signalservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupSessionBackupRequest {

    @NotBlank
    private String groupId;

    @NotNull
    private UUID distributionId;

    private Integer deviceId;

    private UUID remoteUserKey;

    private UUID remoteDevice;

    @NotBlank
    private String serializedSenderKey;

    private String aesAlg;

    private String version;

    private String salt;
}
