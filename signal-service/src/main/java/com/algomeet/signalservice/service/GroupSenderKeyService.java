package com.algomeet.signalservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
import com.algomeet.signalservice.enums.GroupRole;
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
	    if (group == null || group.getMembers() == null || group.getMembers().isEmpty()) {
	        return Collections.emptyList();
	    }

	    Set<MemberResponse> members = group.getMembers();
	    String senderKeyStr = senderUserKey.toString();
	    
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

	    if (existingKeys != null) {
	        for (GroupSenderKeyView e : existingKeys) {
	            // Mapping GroupSenderKey parts to a UserDeviceId for direct comparison later	        	
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
				.findByIdReceiverUserKeyAndIdReceiverDeviceIdAndIdGroupIdAndDeletedAtIsNull(
						receiverUserKey, receiverDeviceId, groupId)
				.stream()
				.map(GroupSenderKeyMapper::toDto)
				.toList();
	}

	@Transactional
	public void delete(GroupSenderKeyId id) {	
		if(repository.findById(id).isEmpty()) {
			throw new RecordNotFoundException("User device group sender key not found");
		}
		
		repository.deleteById(id);
	}	

	/**
	 * Soft deletes a GroupSenderKey record.
	 *
	 * <p>The record is not physically removed because it may still be referenced
	 * by other queries for audit or synchronization purposes.
	 * The encrypted payload is cleared to reduce storage usage.</p>
	 *
	 * @param id composite key identifying the record
	 */
	@Transactional
	public void markAsProcessed(GroupSenderKeyId id) {
		Optional<GroupSenderKey>  senderKeyOpt = repository.findById(id);
		if(senderKeyOpt.isEmpty()) {
			throw new RecordNotFoundException("Group sender key not found");
		}
		
		if(senderKeyOpt.isPresent() && senderKeyOpt.get().getDeletedAt() != null) {
			throw new RecordNotFoundException("Group sender key not found");
		}
		
	    repository.findById(id).ifPresent(entity -> {
	        entity.setSkdmCipher(null); // clear sensitive payload
	        entity.setDeletedAt(Instant.now());
	    });
	}
		
	@Transactional
	public void delete(String currentUserKey, String groupId) {
		
	    GroupResponse group = groupClient.getGroupById(groupId);
	    if (!(group == null || group.getMembers() == null || group.getMembers().isEmpty())) {
	    	if(!(group.getMembers().stream()
	    			.anyMatch(m -> m.getUserKey().equals(currentUserKey) 
	    					&& (GroupRole.OWNER ==  m.getRole() || GroupRole.ADMIN ==  m.getRole())))) {
	    		return;
	    	}
	    }
	    
		if(repository.findFirstByIdGroupId(groupId).isEmpty()) {
			throw new RecordNotFoundException("Group sender keys not found");
		}
		
		repository.deleteByIdGroupId(groupId);
	}	
}

