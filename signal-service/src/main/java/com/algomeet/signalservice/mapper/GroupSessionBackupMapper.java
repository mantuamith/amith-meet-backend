package com.algomeet.signalservice.mapper;

import com.algomeet.signalservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalservice.entity.GroupSessionBackup;
import com.algomeet.signalservice.entity.GroupSessionBackupId;

import java.util.UUID;

public class GroupSessionBackupMapper {

    public static GroupSessionBackup toEntity(UUID userKey, GroupSessionBackupRequest request) {
        GroupSessionBackup backup = new GroupSessionBackup();
        backup.setId(new GroupSessionBackupId(userKey, request.getGroupId(), request.getDistributionId()));
        backup.setDeviceId(request.getDeviceId());
        backup.setRemoteUserKey(request.getRemoteUserKey());
        backup.setRemoteDeviceId(request.getRemoteDeviceId());
        backup.setSerializedSenderKey(request.getSerializedSenderKey());
        backup.setAesAlg(request.getAesAlg());
        backup.setVersion(request.getVersion());
        backup.setSalt(request.getSalt());
        return backup;
    }

    public static GroupSessionBackupResponse toDto(GroupSessionBackup entity) {
        GroupSessionBackupResponse dto = new GroupSessionBackupResponse();
        dto.setGroupId(entity.getId().getGroupId());
        dto.setDistributionId(entity.getId().getDistributionId());
        dto.setDeviceId(entity.getDeviceId());
        dto.setRemoteUserKey(entity.getRemoteUserKey());
        dto.setRemoteDeviceId(entity.getRemoteDeviceId());
        dto.setSerializedSenderKey(entity.getSerializedSenderKey());
        dto.setAesAlg(entity.getAesAlg());
        dto.setVersion(entity.getVersion());
        dto.setSalt(entity.getSalt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
