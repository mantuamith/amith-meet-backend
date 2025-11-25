package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.algomeet.signalservice.entity.GroupSenderKey;

public interface GroupSenderKeyRepository extends JpaRepository<GroupSenderKey, Long> {

    GroupSenderKey findBySenderUserKeyAndSenderDeviceIdAndGroupId(
            UUID senderUserKey, Integer senderDeviceId, String groupId);
    
    List<GroupSenderKey> findByReceiverUserKeyAndReceiverDeviceIdAndGroupId(
            UUID senderUserKey, Integer senderDeviceId, String groupId);
}
