package com.algomeet.opaqueservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.opaqueservice.entity.UserSecureStore;
import com.algomeet.opaqueservice.entity.UserSecureStoreId;
import com.algomeet.opaqueservice.enums.CredentialType;

public interface UserSecureStoreRepository extends JpaRepository<UserSecureStore, UserSecureStoreId> {
	@Transactional(readOnly = true)
    List<UserSecureStore> findByIdUserKey(UUID userKey);

	@Transactional(readOnly = true)
    UserSecureStore findByIdUserKeyAndIdType(UUID userKey, CredentialType type);
}