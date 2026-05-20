package com.algomeet.signalservice.mapper;

import java.util.UUID;

import com.algomeet.signalservice.dto.GroupSenderKeyBackupRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupResponse;
import com.algomeet.signalservice.entity.GroupSenderKeyBackup;
import com.algomeet.signalservice.entity.GroupSenderKeyBackupId;

public class GroupSenderKeyBackupMapper {

    public static GroupSenderKeyBackup toEntity(UUID userKey, GroupSenderKeyBackupRequest request) {
        GroupSenderKeyBackup entity = new GroupSenderKeyBackup();
        entity.setId(new GroupSenderKeyBackupId(
        		userKey,
                request.getGroupId(),
                request.getDistributionId()
        ));
        entity.setSerializedSkdm(request.getSerializedSkdm());
        entity.setVersion(request.getVersion());
        entity.setAesAlg(request.getAesAlg());
        entity.setSalt(request.getSalt());
        return entity;
    }

    public static GroupSenderKeyBackupResponse toResponse(GroupSenderKeyBackup entity) {
        GroupSenderKeyBackupResponse response = new GroupSenderKeyBackupResponse();
        response.setUserKey(entity.getId().getUserKey());
        response.setGroupId(entity.getId().getGroupId().toString());
        response.setDistributionId(entity.getId().getDistributionId());
        response.setSerializedSkdm(entity.getSerializedSkdm());
        response.setVersion(entity.getVersion());
        response.setAesAlg(entity.getAesAlg());
        response.setSalt(entity.getSalt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}