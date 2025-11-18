package com.algomeet.opaqueservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.opaqueservice.entity.UserE2eeSecret;
import com.algomeet.opaqueservice.entity.UserE2eeSecretId;
import com.algomeet.opaqueservice.enums.CredentialType;
import com.algomeet.opaqueservice.repository.UserE2eeSecretRepository;

@Service
@Transactional
public class UserE2eeSecretService {
    private final UserE2eeSecretRepository repository;

    public UserE2eeSecretService(UserE2eeSecretRepository repository) {
        this.repository = repository;
    }

    /**
     * Get one E2EE secret by userKey + type
     */
    public UserE2eeSecret getSecret(UUID userKey, CredentialType type) {
        return repository.findByUserKeyAndType(userKey, type);
    }

    /**
     * Get all secrets for a user
     */
    public List<UserE2eeSecret> getSecrets(UUID userKey) {
        return repository.findByUserKey(userKey);
    }

    /**
     * Create or update secret
     */
    public UserE2eeSecret save(UUID userKey, CredentialType type, String masterSecretKey) {
        UserE2eeSecret existing =
            repository.findByUserKeyAndType(userKey, type);

        if (existing == null) {
            existing = new UserE2eeSecret(new UserE2eeSecretId(userKey, type), masterSecretKey);
        } else {
            existing.setSecretKey(masterSecretKey);
        }

        return repository.save(existing);
    }

    /**
     * Delete a single secret
     */
    public void delete(UUID userKey, CredentialType type) {
        UserE2eeSecret secret =
            repository.findByUserKeyAndType(userKey, type);

        if (secret != null) {
            repository.delete(secret);
        }
    }

    /**
     * Delete all secrets for a user
     */
    public void deleteAll(UUID userKey) {
        List<UserE2eeSecret> secrets = repository.findByUserKey(userKey);
        repository.deleteAll(secrets);
    }
}