package com.algomeet.signalservice.service;

import com.algomeet.signalservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalservice.entity.GroupSessionBackup;
import com.algomeet.signalservice.entity.GroupSessionBackupId;
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

    public GroupSessionBackupResponse findBackup(UUID userKey, String groupId, UUID distributionId) {
        return repository.findById(new GroupSessionBackupId(userKey, groupId, distributionId))
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
    public void deleteBackup(UUID userKey, String groupId, UUID distributionId) {
    	repository.findById(new GroupSessionBackupId(userKey, groupId, distributionId))
    	.orElseThrow(() -> new RecordNotFoundException("Group session backup not found"));

    	repository.deleteByIdUserKeyAndIdGroupIdAndIdDistributionId(userKey, groupId, distributionId);
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