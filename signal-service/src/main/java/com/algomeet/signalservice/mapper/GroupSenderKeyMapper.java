package com.algomeet.signalservice.mapper;

import java.util.UUID;

import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.entity.GroupSenderKey;

public class GroupSenderKeyMapper {

    public static GroupSenderKey toEntity(UUID senderUserKey, Integer senderDeviceId, String groupId, GroupSenderKeyRequest req) {
        GroupSenderKey entity = new GroupSenderKey();

        entity.setReceiverUserKey(req.getReceiverUserKey());
        entity.setReceiverDeviceId(senderDeviceId);
        entity.setGroupId(groupId);
        entity.setSenderUserKey(senderUserKey);
        entity.setSenderDeviceId(senderDeviceId);
        entity.setSkdmCipher(req.getSkdmCipher());

        // createdAt is set by @PrePersist
        return entity;
    }

    public static GroupSenderKeyResponse toDto(GroupSenderKey entity) {
        GroupSenderKeyResponse dto = new GroupSenderKeyResponse();
        dto.setId(entity.getId());
        dto.setReceiverUserKey(entity.getReceiverUserKey());
        dto.setReceiverDeviceId(entity.getReceiverDeviceId());
        dto.setGroupId(entity.getGroupId());
        dto.setSenderUserKey(entity.getSenderUserKey());
        dto.setSenderDeviceId(entity.getSenderDeviceId());
        dto.setSkdmCipher(entity.getSkdmCipher());
        dto.setCreatedAt(entity.getCreatedAt());

        return dto;
    }
}