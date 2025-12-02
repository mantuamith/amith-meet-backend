package com.algomeet.signalservice.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.dto.GroupSenderKeyBackupRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyBackupUpdateRequest;
import com.algomeet.signalservice.entity.GroupSenderKeyBackup;
import com.algomeet.signalservice.entity.GroupSenderKeyBackupId;
import com.algomeet.signalservice.mapper.GroupSenderKeyBackupMapper;
import com.algomeet.signalservice.repository.GroupSenderKeyBackupRepository;

import lombok.RequiredArgsConstructor;

import com.algomeet.signalservice.exceptions.GroupSenderKeyBackupExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;

@Service
@RequiredArgsConstructor

public class GroupSenderKeyBackupService {
	private final GroupSenderKeyBackupRepository repository;

	@Transactional
	public GroupSenderKeyBackupResponse save(UUID userKey, GroupSenderKeyBackupRequest request) {
		if(repository.findById(new GroupSenderKeyBackupId(userKey, request.getGroupId(), request.getDistributionId())).isPresent()) {
			throw new GroupSenderKeyBackupExistsException("Group sender key backup already exists");
		}

		GroupSenderKeyBackup entity = GroupSenderKeyBackupMapper.toEntity(userKey, request);
		GroupSenderKeyBackup saved = repository.save(entity);
		return GroupSenderKeyBackupMapper.toResponse(saved);
	}

	public GroupSenderKeyBackupResponse update(UUID userKey, String groupId, UUID distributionId, GroupSenderKeyBackupUpdateRequest request) {
		return repository.findById(new GroupSenderKeyBackupId(userKey, groupId, distributionId))
				.map(entity -> {

					entity.setSerializedSkdm(request.getSerializedSkdm());
					entity.setAesAlg(request.getAesAlg());
					entity.setSalt(request.getSalt());
					entity.setVersion(request.getVersion());
					return GroupSenderKeyBackupMapper.toResponse(repository.save(entity));
				})
				.orElseThrow(() -> new RecordNotFoundException("Group sender key backup not found"));
	}

	public Optional<GroupSenderKeyBackupResponse> findById(UUID userKey, String groupId, UUID distributionId) {
		return repository.findById(new GroupSenderKeyBackupId(userKey, groupId, distributionId))
				.map(GroupSenderKeyBackupMapper::toResponse);
	}

	public List<GroupSenderKeyBackupResponse> findByUser(UUID userKey) {
		return repository.findByIdUserKey(userKey)
				.stream()
				.map(GroupSenderKeyBackupMapper::toResponse)
				.collect(Collectors.toList());
	}

	public List<GroupSenderKeyBackupResponse> findByGroup(String groupId) {
		return repository.findByIdGroupId(groupId)
				.stream()
				.map(GroupSenderKeyBackupMapper::toResponse)
				.collect(Collectors.toList());
	}

	public void delete(UUID userKey, String groupId, UUID distributionId) {
		repository.findById(new GroupSenderKeyBackupId(userKey, groupId, distributionId))
		.orElseThrow(() -> new RecordNotFoundException("Group sender key backup not found"));

		repository.deleteById(new GroupSenderKeyBackupId(userKey, groupId, distributionId));
	}
}
