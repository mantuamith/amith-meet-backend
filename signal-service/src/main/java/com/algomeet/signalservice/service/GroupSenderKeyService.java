package com.algomeet.signalservice.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.entity.GroupSenderKey;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.GroupSenderKeyMapper;
import com.algomeet.signalservice.repository.GroupSenderKeyRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;

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

		List<GroupSenderKey> list = repository.findByIdSenderUserKeyAndIdSenderDeviceIdAndIdGroupId(
				senderUserKey, senderDeviceId, groupId);

		return list.stream().map(GroupSenderKeyMapper::toDto).toList();
	}

	public List<GroupSenderKeyResponse> longPoll(
			UUID receiverUserKey, Integer receiverDeviceId, String groupId, long timeoutMs) {

		deviceRepository.findById(new UserDeviceId(receiverUserKey, receiverDeviceId))
		.orElseThrow(() -> new RecordNotFoundException("User device ID not found"));

		long start = System.currentTimeMillis();        
		
		do {
			List<GroupSenderKey> pending = repository.findByIdReceiverUserKeyAndIdReceiverDeviceIdAndIdGroupId(
					receiverUserKey, receiverDeviceId, groupId);

			if (!pending.isEmpty()) {
				return pending.stream()
						.map(GroupSenderKeyMapper::toDto)
						.collect(Collectors.toList());
			}

			try {
				if (timeoutMs >= 500) {
					Thread.sleep(500); // small wait
				}
			} catch (InterruptedException ignored) {}
		} while (System.currentTimeMillis() - start < timeoutMs);

		return List.of(); // timeout
	}

	@Transactional
	public void delete(
			UUID receiverUserKey, UUID senderUserKey, Integer senderDeviceId, String groupId) {
		repository.deleteByIdSenderUserKeyAndIdSenderDeviceIdAndIdReceiverUserKeyAndIdGroupId(senderUserKey, senderDeviceId, receiverUserKey, groupId);
	}
}

