package com.algomeet.signalservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.algomeet.signalservice.dto.OneTimePreKeyRequest;
import com.algomeet.signalservice.dto.OneTimePreKeyResponse;
import com.algomeet.signalservice.dto.OneTimePreKeysRequest;
import com.algomeet.signalservice.entity.OneTimePreKey;
import com.algomeet.signalservice.entity.UserDeviceId;
import com.algomeet.signalservice.exceptions.OneTimePreKeyIsNotAvailableException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.OneTimePreKeyMapper;
import com.algomeet.signalservice.repository.OneTimePreKeyRepository;
import com.algomeet.signalservice.repository.UserDeviceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OneTimePreKeyService {
	private final OneTimePreKeyRepository repository;
	private final UserDeviceRepository deviceRepository;

	public OneTimePreKeyResponse update(long id, OneTimePreKeyRequest request) {
		OneTimePreKey entity = repository.findById(id)
				.orElseThrow(() -> new RecordNotFoundException("OneTimePreKey not found"));

		entity.setPreKeyId(request.getPreKeyId());
		entity.setPublicKey(request.getPublicKey());
		return OneTimePreKeyMapper.toResponse(repository.save(entity));
	}

	public List<OneTimePreKeyResponse> create(UUID userKey, Integer deviceId, OneTimePreKeysRequest request) {
		deviceRepository.findById(new UserDeviceId(userKey, deviceId))
		.orElseThrow(() -> new RecordNotFoundException("User device ID not found"));

		List<OneTimePreKey> preKeys = new ArrayList<>();

		for (OneTimePreKeyRequest preKeyReq : request.getPreKeys()) {
			OneTimePreKey entity = OneTimePreKeyMapper.toEntity(userKey, deviceId, preKeyReq);
			preKeys.add(entity);
		}

		return repository.saveAll(preKeys).stream().map(OneTimePreKeyMapper::toResponse).toList();
	}

	public OneTimePreKeyResponse getAvailable(UUID userKey, Integer deviceId) {
		deviceRepository.findById(new UserDeviceId(userKey, deviceId))
		.orElseThrow(() -> new RecordNotFoundException("User device ID not found"));

		OneTimePreKey preKey = repository.findFirstByUserKeyAndDeviceIdAndUsedFalseOrderByIdAsc(userKey, deviceId);
		if (preKey == null) {
			throw new OneTimePreKeyIsNotAvailableException("User device don't have available one time prekey");
		}

		// Remove to prevent re-used of one time key
		repository.deleteById(preKey.getId());				
		return OneTimePreKeyMapper.toResponse(preKey);
	}

	public Long getAvailablePrekeysCount(UUID userKey, Integer deviceId) {	
		deviceRepository.findById(new UserDeviceId(userKey, deviceId))
		.orElseThrow(() -> new RecordNotFoundException("User device ID not found"));

		return repository.countByUserKeyAndDeviceIdAndUsedFalse(userKey, deviceId);
	}

	@Transactional
	public void delete(UUID userKey, Integer deviceId) {		
		if (CollectionUtils.isEmpty(repository.findByUserKeyAndDeviceId(userKey, deviceId))) {
			throw new RecordNotFoundException("User device one-time prekey(s) not found");
		}
		
		repository.deleteByUserKeyAndDeviceId(userKey, deviceId);
	}
}