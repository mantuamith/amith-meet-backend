package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.algomeet.signalservice.entity.GroupSenderKey;
import com.algomeet.signalservice.entity.GroupSenderKeyId;

import jakarta.transaction.Transactional;

public interface GroupSenderKeyRepository extends JpaRepository<GroupSenderKey, GroupSenderKeyId> {

	List<GroupSenderKey> findByIdSenderUserKeyAndIdSenderDeviceIdAndIdGroupId(
            UUID senderUserKey, Integer senderDeviceId, String groupId);
    
    List<GroupSenderKey> findByIdReceiverUserKeyAndIdReceiverDeviceIdAndIdGroupId(
            UUID senderUserKey, Integer senderDeviceId, String groupId);
    
    Optional<GroupSenderKey> findByIdSenderUserKeyAndIdSenderDeviceIdAndIdReceiverUserKeyAndIdGroupId(
            UUID senderUserKey,
            Integer senderDeviceId,
            UUID receiverUserKey,
            String groupId);
    
    @Modifying
    @Transactional
    void deleteByIdSenderUserKeyAndIdSenderDeviceIdAndIdReceiverUserKeyAndIdGroupId(
            UUID senderUserKey,
            Integer senderDeviceId,
            UUID receiverUserKey,
            String groupId);
}
