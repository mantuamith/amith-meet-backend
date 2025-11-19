package com.algomeet.opaqueservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.algomeet.opaqueservice.entity.UserSecureStore;
import com.algomeet.opaqueservice.entity.UserSecureStoreId;
import com.algomeet.opaqueservice.enums.CredentialType;
import com.algomeet.opaqueservice.repository.UserSecureStoreRepository;

@Service
@Transactional
public class UserSecureStoreService {
	private final UserSecureStoreRepository repository;

	public UserSecureStoreService(UserSecureStoreRepository repository) {
		this.repository = repository;
	}

	/**
	 * Get one E2EE secret by userKey + type
	 */
	public UserSecureStore getSecret(UUID userKey, CredentialType type) {
		return repository.findByIdUserKeyAndIdType(userKey, type);
	}

	/**
	 * Get all secrets for a user
	 */
	public List<UserSecureStore> getSecrets(UUID userKey) {
		return repository.findByIdUserKey(userKey);
	}

	/**
	 * Create or update secret
	 */
	public UserSecureStore save(UUID userKey, CredentialType type, String rec, String masterSecretKey) {
		UserSecureStore existing =
				repository.findByIdUserKeyAndIdType(userKey, type);

		if (existing == null) {
			existing = new UserSecureStore(new UserSecureStoreId(userKey, type), rec, masterSecretKey);
		} else {
			if(StringUtils.hasLength(masterSecretKey)) {
				existing.setMasterSecretKey(masterSecretKey);
			}

			if(StringUtils.hasLength(rec)) {
				existing.setRec(rec);
			}
		}

		return repository.save(existing);
	}

	/**
	 * Delete a single secret
	 */
	public void delete(UUID userKey, CredentialType type) {
		UserSecureStore masterSecret =
				repository.findByIdUserKeyAndIdType(userKey, type);

		if (masterSecret != null) {
			repository.delete(masterSecret);
		}
	}

	/**
	 * Delete all secrets for a user
	 */
	public void deleteAll(UUID userKey) {
		List<UserSecureStore> massterSecrets = repository.findByIdUserKey(userKey);
		repository.deleteAll(massterSecrets);
	}
}