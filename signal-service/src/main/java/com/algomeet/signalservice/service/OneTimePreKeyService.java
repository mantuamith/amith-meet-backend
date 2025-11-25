package com.algomeet.signalservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.signalservice.dto.OneTimePreKeyRequest;
import com.algomeet.signalservice.dto.OneTimePreKeyResponse;
import com.algomeet.signalservice.dto.OneTimePreKeysRequest;
import com.algomeet.signalservice.entity.OneTimePreKey;
import com.algomeet.signalservice.exceptions.OneTimePreKeyIsNotAvailableException;
import com.algomeet.signalservice.mapper.OneTimePreKeyMapper;
import com.algomeet.signalservice.repository.OneTimePreKeyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OneTimePreKeyService {
	private final OneTimePreKeyRepository repository;
	private final OneTimePreKeyMapper mapper;

	public OneTimePreKeyResponse update(long id, OneTimePreKeyRequest request) {
		OneTimePreKey entity = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("OneTimePreKey not found"));
		entity.setPreKeyId(request.getPreKeyId());
		entity.setPublicKey(request.getPublicKey());
		return mapper.toResponse(repository.save(entity));
	}
	
	public void create(UUID userKey, Integer deviceId, OneTimePreKeysRequest request) {
		for (OneTimePreKeyRequest preKey : request.getPreKeys()) {
			OneTimePreKey entity = mapper.toEntity(userKey, deviceId, preKey);
			entity.setCreatedAt(java.time.Instant.now());
		}
	}

	public OneTimePreKeyResponse getAvailable(UUID userKey, Integer deviceId) {
		OneTimePreKey preKey = repository.findFirstByUserKeyAndDeviceIdAndUsedFalseOrderByIdAsc(userKey, deviceId);
		if (preKey == null) {
			throw new OneTimePreKeyIsNotAvailableException("User device don't have available one time prekey");
		}

		// Remove to prevent re-used of one time key
		repository.deleteById(preKey.getId());				
		return mapper.toResponse(preKey);
	}
	
	public Long getAvailablePrekeysCount(UUID userKey, Integer deviceId) {				
		return repository.countByUserKeyAndDeviceIdAndUsedFalse(userKey, deviceId);
	}
	
	public void delete(UUID userKey, Integer deviceId) {
		repository.deleteByUserKeyAndDeviceId(userKey, deviceId);
	}
}