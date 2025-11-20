package com.algomeet.opaqueservice.service;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.algomeet.opaqueservice.dto.UserMasterSecretRequest;
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
	public UserSecureStore getMasterSecret(UUID userKey, CredentialType type) {
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
	public UserSecureStore save(UUID userKey, String rec, UserMasterSecretRequest req) {
		UserSecureStore existing =
				repository.findByIdUserKeyAndIdType(userKey, req.getType());

		if (existing == null) {
			existing = new UserSecureStore(
					new UserSecureStoreId(userKey, req.getType()), 
					rec, 
					req.getMasterSecretKey(),
					req.getAlgorithm(),
					req.getVersion(),
					req.getSalt()
					);
		} else {
			if(StringUtils.hasLength(req.getMasterSecretKey())) {
				existing.setMasterSecretKey(req.getMasterSecretKey());
			}

			if(StringUtils.hasLength(rec)) {
				existing.setRec(rec);
			}
			
			if(StringUtils.hasLength(req.getAlgorithm())) {
				existing.setAlgorithm(req.getAlgorithm());
			}
			
			if(StringUtils.hasLength(req.getVersion())) {
				existing.setVersion(req.getVersion());
			}
			
			if(StringUtils.hasLength(req.getSalt())) {
				existing.setSalt(req.getSalt());
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