package com.algomeet.opaqueservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.algomeet.opaqueservice.entity.UserE2eeSecret;
import com.algomeet.opaqueservice.entity.UserE2eeSecretId;
import com.algomeet.opaqueservice.enums.CredentialType;

public interface UserE2eeSecretRepository extends JpaRepository<UserE2eeSecret, UserE2eeSecretId> {

    List<UserE2eeSecret> findByUserKey(UUID userKey);

    UserE2eeSecret findByUserKeyAndType(UUID userKey, CredentialType type);
}