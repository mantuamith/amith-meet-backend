package com.algomeet.signalservice.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.entity.GroupSenderKey;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.GroupSenderKeyMapper;
import com.algomeet.signalservice.repository.GroupSenderKeyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupSenderKeyService {
    private final GroupSenderKeyRepository repository;

    public GroupSenderKeyResponse create(UUID senderUserKey, Integer senderDeviceId, String groupId, GroupSenderKeyRequest request) {
        GroupSenderKey entity = GroupSenderKeyMapper.toEntity(senderUserKey, senderDeviceId, groupId, request);
        GroupSenderKey saved = repository.save(entity);
        return GroupSenderKeyMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public GroupSenderKeyResponse get(UUID senderUserKey, Integer senderDeviceId, String groupId) {
        GroupSenderKey entity = repository.findBySenderUserKeyAndSenderDeviceIdAndGroupId(
                senderUserKey, senderDeviceId, groupId);

        if (entity == null) {
        	throw new RecordNotFoundException("Group sender keys not found");
        }

        return GroupSenderKeyMapper.toDto(entity);
    }
        
    public List<GroupSenderKeyResponse> longPoll(
    		UUID receiverUserKey, Integer receiverDeviceId, String groupId, long timeoutMs) {

        long start = System.currentTimeMillis();        
        while (System.currentTimeMillis() - start < timeoutMs) {

            List<GroupSenderKey> pending = repository.findByReceiverUserKeyAndReceiverDeviceIdAndGroupId(
                    receiverUserKey, receiverDeviceId, groupId);

            if (!pending.isEmpty()) {
                return pending.stream()
                        .map(GroupSenderKeyMapper::toDto)
                        .collect(Collectors.toList());
            }

            try {
                Thread.sleep(500); // small wait
            } catch (InterruptedException ignored) {}
        }

        return List.of(); // timeout
    }
}

