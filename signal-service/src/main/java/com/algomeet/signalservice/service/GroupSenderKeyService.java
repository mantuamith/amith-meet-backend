package com.algomeet.signalservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.entity.GroupSenderKey;
import com.algomeet.signalservice.entity.GroupSenderKeyId;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.GroupSenderKeyMapper;
import com.algomeet.signalservice.mapper.GroupSenderKeyViewMapper;
import com.algomeet.signalservice.repository.GroupSenderKeyRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;
import com.algomeet.signalservice.view.GroupSenderKeyView;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupSenderKeyService {
	private final GroupSenderKeyRepository repository;
	private final UserDeviceRepository deviceRepository;

	public GroupSenderKeyResponse create(UUID senderUserKey, Integer senderDeviceId, String groupId, GroupSenderKeyRequest request) {
		deviceRepository.findById(new UserDeviceId(senderUserKey, senderDeviceId))
		.orElseThrow(() -> new RecordNotFoundException("User device ID not found"));

		GroupSenderKey entity = GroupSenderKeyMapper.toEntity(senderUserKey, senderDeviceId, groupId, request);
		GroupSenderKey saved = repository.save(entity);
		return GroupSenderKeyMapper.toDto(saved);
	}

	@Transactional(readOnly = true)
	public List<GroupSenderKeyResponse> getList(UUID senderUserKey, Integer senderDeviceId, String groupId) {
		deviceRepository.findById(new UserDeviceId(senderUserKey, senderDeviceId))
		.orElseThrow(() -> new RecordNotFoundException("User device ID not found"));

		List<GroupSenderKeyView> list = repository.findByIdSenderUserKeyAndIdSenderDeviceIdAndIdGroupId(
				senderUserKey, senderDeviceId, groupId);

		return list.stream().map(GroupSenderKeyViewMapper::toDto).toList();
	}

	public List<GroupSenderKeyResponse> longPoll(
			UUID receiverUserKey, Integer receiverDeviceId, String groupId, long timeoutMs) {
		return getSenderKeys(
				receiverUserKey, receiverDeviceId, groupId);
	}
	
	public List<GroupSenderKeyResponse> getSenderKeys(
			UUID receiverUserKey, Integer receiverDeviceId, String groupId) {

		deviceRepository.findById(new UserDeviceId(receiverUserKey, receiverDeviceId))
		.orElseThrow(() -> new RecordNotFoundException("User device ID not found"));

		return repository
		        .findByIdReceiverUserKeyAndIdReceiverDeviceIdAndIdGroupId(
		                receiverUserKey, receiverDeviceId, groupId)
		        .stream()
		        .map(GroupSenderKeyMapper::toDto)
		        .toList();
	}
	
	@Transactional
	public void delete(GroupSenderKeyId id) {		
		repository.deleteById(id);
	}	
}

