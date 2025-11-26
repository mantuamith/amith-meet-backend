package com.algomeet.signalservice.service;

import com.algomeet.signalservice.dto.GroupSessionBackupRequest;
import com.algomeet.signalservice.dto.GroupSessionBackupResponse;
import com.algomeet.signalservice.entity.GroupSessionBackup;
import com.algomeet.signalservice.repository.GroupSessionBackupRepository;
import com.algomeet.signalservice.mapper.GroupSessionBackupMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<GroupSessionBackupResponse> findBackupByDistribution(UUID userKey, UUID distributionId) {
        return repository.findByIdUserKeyAndIdDistributionId(userKey, distributionId)
                .stream()
                .map(GroupSessionBackupMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteBackup(UUID userKey, String groupId, UUID distributionId) {
        repository.deleteByIdUserKeyAndIdGroupIdAndIdDistributionId(userKey, groupId, distributionId);
    }

    @Transactional
    public void deleteAllUserBackups(UUID userKey) {
        repository.deleteByIdUserKey(userKey);
    }
}