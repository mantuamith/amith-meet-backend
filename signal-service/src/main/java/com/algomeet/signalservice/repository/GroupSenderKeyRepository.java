package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.algomeet.signalservice.entity.GroupSenderKey;
import com.algomeet.signalservice.entity.GroupSenderKeyId;
import com.algomeet.signalservice.view.GroupSenderKeyView;

import jakarta.transaction.Transactional;

public interface GroupSenderKeyRepository extends JpaRepository<GroupSenderKey, GroupSenderKeyId> {

	@Query(value = """
	        SELECT 
	            group_id AS groupId, 
	            receiver_user_key AS receiverUserKey, 
	            receiver_device_id AS receiverDeviceId, 
	            sender_user_key AS senderUserKey, 
	            sender_device_id AS senderDeviceId, 
	            created_at AS createdAt
	        FROM signal_group_sender_keys
	        WHERE sender_user_key = :senderUserKey 
	          AND sender_device_id = :senderDeviceId 
	          AND group_id = :groupId
	        """, nativeQuery = true)
	List<GroupSenderKeyView> findByIdSenderUserKeyAndIdSenderDeviceIdAndIdGroupId(
			@Param("senderUserKey") UUID senderUserKey, 
		    @Param("senderDeviceId") Integer senderDeviceId, 
		    @Param("groupId") String groupId);
    
    List<GroupSenderKey> findByIdReceiverUserKeyAndIdReceiverDeviceIdAndIdGroupId(
            UUID receiverUserKey, Integer receiverDeviceId, String groupId);
   
    @Modifying
    @Transactional
    void deleteByIdReceiverUserKeyAndIdReceiverDeviceId(
            UUID receiverUserKey,
            Integer receiverDeviceId);
}
