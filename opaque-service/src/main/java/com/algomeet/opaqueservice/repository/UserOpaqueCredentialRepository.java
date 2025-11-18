package com.algomeet.opaqueservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.algomeet.opaqueservice.entity.UserOpaqueCredential;
import com.algomeet.opaqueservice.entity.UserOpaqueCredentialId;
import com.algomeet.opaqueservice.enums.CredentialType;

@Repository
public interface UserOpaqueCredentialRepository extends JpaRepository<UserOpaqueCredential, UserOpaqueCredentialId> {

    List<UserOpaqueCredential> findByIdUserKey(UUID userKey);

    UserOpaqueCredential findByIdUserKeyAndIdType(UUID userKey, CredentialType type);

    void deleteByIdUserKeyAndIdType(UUID userKey, CredentialType type);
}