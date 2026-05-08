package com.algomeet.signalservice.mapper;

import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.view.GroupSenderKeyView;

public class GroupSenderKeyViewMapper {

	public static GroupSenderKeyResponse toDto(GroupSenderKeyView entity) {
		GroupSenderKeyResponse dto = new GroupSenderKeyResponse();
		dto.setReceiverUserKey(entity.getReceiverUserKey());
		dto.setReceiverDeviceId(entity.getReceiverDeviceId());
		dto.setGroupId(entity.getGroupId());
		dto.setSenderUserKey(entity.getSenderUserKey());
		dto.setSenderDeviceId(entity.getSenderDeviceId());
		dto.setCreatedAt(entity.getCreatedAt());
		dto.setDeletedAt(entity.getDeletedAt());

		return dto;
	}
}