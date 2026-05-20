package com.algomeet.signalservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
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
			    group_id as groupId, 
			    receiver_user_key as receiverUserKey, 
			    receiver_device_id as receiverDeviceId, 
			    sender_user_key as senderUserKey, 
			    sender_device_id as senderDeviceId, 
			    created_at as createdAt,
			    deleted_at as deletedAt
			FROM signal_group_sender_keys
			WHERE sender_user_key = :senderUserKey 
			  AND sender_device_id = :senderDeviceId 
			  AND group_id = :groupId
			  AND deleted_at is null
			""", nativeQuery = true)
	List<GroupSenderKeyView> findByIdSenderUserKeyAndIdSenderDeviceIdAndIdGroupId(
			@Param("senderUserKey") UUID senderUserKey, 
			@Param("senderDeviceId") Integer senderDeviceId, 
			@Param("groupId") UUID groupId);

	List<GroupSenderKey> findByIdReceiverUserKeyAndIdReceiverDeviceIdAndIdGroupIdAndDeletedAtIsNull(
			UUID receiverUserKey, Integer receiverDeviceId, UUID groupId);

	@Query(value = """
			SELECT 
			    group_id as groupId, 
			    receiver_user_key as receiverUserKey, 
			    receiver_device_id as receiverDeviceId, 
			    sender_user_key as senderUserKey, 
			    sender_device_id as senderDeviceId, 
			    created_at as createdAt,
			    deleted_at as deletedAt
			FROM signal_group_sender_keys
			WHERE sender_user_key = :senderUserKey 
			  AND group_id = :groupId
			""", nativeQuery = true)
	List<GroupSenderKeyView> findByIdSenderUserKeyAndIdGroupId(
			@Param("senderUserKey") UUID senderUserKey, 
			@Param("groupId") UUID groupId);

	@Modifying
	@Transactional
	void deleteByIdReceiverUserKeyAndIdReceiverDeviceId(
			UUID receiverUserKey,
			Integer receiverDeviceId);

	@Query("""
			SELECT g
			FROM GroupSenderKey g
			WHERE g.id.groupId = :groupId
			""")
	List<GroupSenderKey> findFirstByGroupId(@Param("groupId") UUID groupId, Pageable pageable);

	@Modifying
	@Transactional
	void deleteByIdGroupId(UUID groupId);

	@Query("""
			SELECT g
			FROM GroupSenderKey g
			WHERE g.id.senderUserKey = :senderUserKey
			AND g.id.receiverUserKey = :receiverUserKey
			AND g.id.groupId = :groupId
			""")
	List<GroupSenderKeyView> findFirstBySenderUserKeyAndReceiverUserKeyAndGroupId(
			@Param("senderUserKey") UUID senderUserKey,
			@Param("receiverUserKey") UUID receiverUserKey,
			@Param("groupId") UUID groupId, Pageable pageable);

	void deleteByIdSenderUserKeyAndIdReceiverUserKeyAndIdGroupId(UUID senderUserKey, UUID receiverUserKey, UUID groupId);
}
