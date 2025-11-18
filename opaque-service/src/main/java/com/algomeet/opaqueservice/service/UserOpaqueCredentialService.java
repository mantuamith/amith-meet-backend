package com.algomeet.opaqueservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.opaqueservice.entity.UserOpaqueCredential;
import com.algomeet.opaqueservice.entity.UserOpaqueCredentialId;
import com.algomeet.opaqueservice.enums.CredentialType;
import com.algomeet.opaqueservice.repository.UserOpaqueCredentialRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserOpaqueCredentialService {
    private final UserOpaqueCredentialRepository repository;


    public UserOpaqueCredential getCredential(UUID userKey, CredentialType type) {
        return repository.findByIdUserKeyAndIdType(userKey, type);
    }

    public List<UserOpaqueCredential> getAllCredentials(UUID userKey) {
        return repository.findByIdUserKey(userKey);
    }

    @Transactional
    public UserOpaqueCredential saveOrUpdate(UUID userKey, CredentialType type, String rec) {
        UserOpaqueCredentialId id = new UserOpaqueCredentialId(userKey, type);
        UserOpaqueCredential existing = repository.findById(id).orElse(null);

        if (existing != null) {
            existing.setRec(rec);
            return repository.save(existing);
        }

        UserOpaqueCredential created = new UserOpaqueCredential(id, rec);
        return repository.save(created);
    }

    @Transactional
    public void deleteCredential(UUID userKey, CredentialType type) {
        repository.deleteByIdUserKeyAndIdType(userKey, type);
    }
}