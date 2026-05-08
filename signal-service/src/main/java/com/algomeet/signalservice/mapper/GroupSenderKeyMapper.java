package com.algomeet.signalservice.mapper;

import java.util.UUID;

import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.entity.GroupSenderKey;
import com.algomeet.signalservice.entity.GroupSenderKeyId;

public class GroupSenderKeyMapper {

	public static GroupSenderKey toEntity(UUID senderUserKey, Integer senderDeviceId, String groupId, GroupSenderKeyRequest req) {
		GroupSenderKey entity = new GroupSenderKey();
		entity.setId(new GroupSenderKeyId(senderUserKey, 
				senderDeviceId, 
				req.getReceiverUserKey(),
				req.getReceiverDeviceId(),
				groupId
				));

		entity.setSkdmCipher(req.getSkdmCipher());

		// createdAt is set by @PrePersist
		return entity;
	}

	public static GroupSenderKeyResponse toDto(GroupSenderKey entity) {
		GroupSenderKeyResponse dto = new GroupSenderKeyResponse();
		dto.setReceiverUserKey(entity.getId().getReceiverUserKey());
		dto.setReceiverDeviceId(entity.getId().getReceiverDeviceId());
		dto.setGroupId(entity.getId().getGroupId());
		dto.setSenderUserKey(entity.getId().getSenderUserKey());
		dto.setSenderDeviceId(entity.getId().getSenderDeviceId());
		dto.setSkdmCipher(entity.getSkdmCipher());
		dto.setCreatedAt(entity.getCreatedAt());
		dto.setDeletedAt(entity.getDeletedAt());

		return dto;
	}
}