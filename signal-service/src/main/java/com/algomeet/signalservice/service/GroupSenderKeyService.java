package com.algomeet.signalservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.client.GroupClient;
import com.algomeet.signalservice.dto.GroupResponse;
import com.algomeet.signalservice.dto.GroupSenderKeyRequest;
import com.algomeet.signalservice.dto.GroupSenderKeyResponse;
import com.algomeet.signalservice.dto.MemberResponse;
import com.algomeet.signalservice.dto.UserDeviceResponse;
import com.algomeet.signalservice.entity.GroupSenderKey;
import com.algomeet.signalservice.entity.GroupSenderKeyId;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.GroupSenderKeyMapper;
import com.algomeet.signalservice.mapper.GroupSenderKeyViewMapper;
import com.algomeet.signalservice.mapper.UserDeviceMapper;
import com.algomeet.signalservice.repository.GroupSenderKeyRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;
import com.algomeet.signalservice.view.GroupSenderKeyView;
import com.algomeet.signalservice.view.UserDeviceView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GroupSenderKeyService {
	private final GroupSenderKeyRepository repository;
	private final UserDeviceRepository deviceRepository;
	private final GroupClient groupClient;

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

	@Transactional(readOnly = true)
	public List<UserDeviceResponse> getMissingDevices(UUID senderUserKey, String groupId) {
	    // 1. Initial validation and early exit
	    GroupResponse group = groupClient.getGroupById(groupId);
	    log.info("group {} ", group);
	    if (group == null || group.getMembers() == null || group.getMembers().isEmpty()) {
	        return Collections.emptyList();
	    }

	    Set<MemberResponse> members = group.getMembers();
	    String senderKeyStr = senderUserKey.toString();
	    log.info("group {} ", members);
	    
	    // 2. Extract UUIDs - Compare as Strings first to avoid unnecessary UUID parsing
	    List<UUID> groupMemberIds = new ArrayList<>(members.size());
	    for (MemberResponse m : members) {
	        String key = m.getUserKey();
	        if (key != null && !senderKeyStr.equals(key)) {
	            groupMemberIds.add(UUID.fromString(key));
	        }
	    }

	    // If only the sender was in the group, no need to query devices
	    if (groupMemberIds.isEmpty()) {
	        return Collections.emptyList();
	    }

	    // 3. Identify devices that have already been processed
	    // Use the UserDeviceId object itself in the Set for O(1) lookup performance
	    List<GroupSenderKeyView> existingKeys = repository.findByIdSenderUserKeyAndIdGroupId(senderUserKey, groupId);
	    Set<UserDeviceId> processedDeviceIds = new HashSet<>(existingKeys != null ? existingKeys.size() : 0);
	    
	    log.info("existingKeys {} ", existingKeys);
	    if (existingKeys != null) {
	        for (GroupSenderKeyView e : existingKeys) {
	            // Mapping GroupSenderKey parts to a UserDeviceId for direct comparison later
	        	
	        	log.info("GroupSenderKeyView {} ", new UserDeviceId(
		                e.getReceiverUserKey(), 
		                e.getReceiverDeviceId()
		            ));
	            processedDeviceIds.add(new UserDeviceId(
	                e.getReceiverUserKey(), 
	                e.getReceiverDeviceId()
	            ));
	        }
	    }

	    // 4. Batch fetch all devices for the group members
	    List<UserDeviceView> deviceList = deviceRepository.findByIdUserKeyIn(groupMemberIds);
	    log.info("deviceList {} ", deviceList);
	    if (deviceList == null || deviceList.isEmpty()) {
	        return Collections.emptyList();
	    }

	    // 5. Filter out devices that are already in the 'processed' set
	    List<UserDeviceView> missingDevices = new ArrayList<>();
	    for (UserDeviceView device : deviceList) {
	    	log.info("UserDeviceView {} ", device);
	        // device.getId() returns a UserDeviceId, which has optimized equals/hashCode
	        if (!processedDeviceIds.contains(device.getId())) {
	            missingDevices.add(device);
	        }
	    }

	    return missingDevices.stream().map(UserDeviceMapper::toResponse).toList();
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

	/**
	 * Soft delete the record to reduce the database load this record will still be used in other queries.
	 * @param id
	 */
	@Transactional
	public void softDelete(GroupSenderKeyId id) {	
		repository.findById(id).ifPresent(groupSenderKey -> {
			groupSenderKey.setSkdmCipher(null);
			groupSenderKey.setDeletedAt(Instant.now());
			repository.save(groupSenderKey);
		});
	}	

	private String buildReceiverDeviceKey(UUID receiverUserKey, Integer receiverDeviceId) {
		return receiverUserKey + "_" + receiverDeviceId;
	}
}

