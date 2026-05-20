package com.algomeet.signalservice.service;

import com.algomeet.signalservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalservice.entity.GroupSessionBackup;
import com.algomeet.signalservice.entity.GroupSessionBackupId;
import com.algomeet.signalservice.exceptions.GroupSessionBackupExistsException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.repository.GroupSessionBackupRepository;
import com.algomeet.signalservice.mapper.GroupSessionBackupMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupSessionBackupService {

    private final GroupSessionBackupRepository repository;

    @Transactional
    public GroupSessionBackupResponse saveBackup(UUID userKey, GroupSessionBackupRequest request) {    	
    	// Inbound session backup must have chain key == 0, prevent from replacing the initial stage of session.
    	if (request.isInbound()) {
    		if(repository.findById(
    				new GroupSessionBackupId(userKey, request.getGroupId(), request.getDistributionId(), request.isInbound())).isPresent()) {
    			throw new GroupSessionBackupExistsException("Group session backup exists");
    		}
    	}
    	
        GroupSessionBackup entity = GroupSessionBackupMapper.toEntity(userKey, request);
        GroupSessionBackup saved = repository.save(entity);
        return GroupSessionBackupMapper.toDto(saved);
    }

    public List<GroupSessionBackupResponse> findBackups(UUID userKey) {
        return repository.findByIdUserKey(userKey)
                .stream()
                .map(GroupSessionBackupMapper::toDto)
                .collect(Collectors.toList());
    }

    public GroupSessionBackupResponse findBackup(UUID userKey, UUID groupId, UUID distributionId, boolean isInbound) {
        return repository.findById(new GroupSessionBackupId(userKey, groupId, distributionId, isInbound))
                .map(GroupSessionBackupMapper::toDto)
                .orElseThrow(() -> new RecordNotFoundException("Group session backup not found"));
    }
    
    public List<GroupSessionBackupResponse> findBackupByDevice(UUID userKey, Integer deviceId) {
        return repository.findByIdUserKeyAndDeviceId(userKey, deviceId)
                .stream()
                .map(GroupSessionBackupMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteBackup(UUID userKey, UUID groupId, UUID distributionId, boolean isInbound) {
    	repository.findById(new GroupSessionBackupId(userKey, groupId, distributionId, isInbound))
    	.orElseThrow(() -> new RecordNotFoundException("Group session backup not found"));

    	repository.deleteById(new GroupSessionBackupId(userKey, groupId, distributionId, isInbound));
    }
    
    public void deleteBackupByDevice(UUID userKey, Integer deviceId) {
    	if (CollectionUtils.isEmpty(repository.findByIdUserKeyAndDeviceId(userKey, deviceId))) {
    		throw new RecordNotFoundException("Group session backup not found");
    	}
    	
        repository.deleteByIdUserKeyAndDeviceId(userKey, deviceId);
    }

    @Transactional
    public void deleteAllUserBackups(UUID userKey) {    	
    	if (CollectionUtils.isEmpty(repository.findByIdUserKey(userKey))) {
    		throw new RecordNotFoundException("Group session backup not found");
    	}
    	
        repository.deleteByIdUserKey(userKey);
    }
}