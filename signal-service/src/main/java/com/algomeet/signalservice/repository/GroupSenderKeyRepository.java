package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import com.algomeet.signalservice.entity.GroupSenderKey;
import com.algomeet.signalservice.entity.GroupSenderKeyId;

public interface GroupSenderKeyRepository extends JpaRepository<GroupSenderKey, GroupSenderKeyId> {

	List<GroupSenderKey> findByIdSenderUserKeyAndIdSenderDeviceIdAndIdGroupId(
            UUID senderUserKey, Integer senderDeviceId, String groupId);
    
    List<GroupSenderKey> findByIdReceiverUserKeyAndIdReceiverDeviceIdAndIdGroupId(
            UUID senderUserKey, Integer senderDeviceId, String groupId);
    
    @Modifying
    void deleteByIdSenderUserKeyAndIdSenderDeviceIdAndIdReceiverUserKeyAndIdGroupId(
            UUID senderUserKey,
            Integer senderDeviceId,
            UUID receiverUserKey,
            String groupId);
}
