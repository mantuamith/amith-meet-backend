package com.algomeet.signalservice.service;

import org.springframework.stereotype.Service;

import com.algomeet.signalservice.dto.KyberPreKeyRequest;
import com.algomeet.signalservice.dto.KyberPreKeyResponse;
import com.algomeet.signalservice.entity.KyberPreKey;
import com.algomeet.signalservice.entity.KyberPreKeyId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.KyberPreKeyMapper;
import com.algomeet.signalservice.repository.KyberPreKeyRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class KyberPreKeyService {
	private final KyberPreKeyRepository repository;

	public KyberPreKeyResponse getPreKey(KyberPreKeyId id) {
		KyberPreKey entity = repository.findById(id)
				.orElseThrow(() -> new RecordNotFoundException("Kyber PreKey not found"));
		return KyberPreKeyMapper.toResponse(entity);
	}

	public KyberPreKeyResponse updatePreKey(KyberPreKeyId id, KyberPreKeyRequest request) {
		KyberPreKey entity = repository.findById(id)
				.orElseThrow(() -> new RecordNotFoundException("Kyber PreKey not found"));

		entity.setKyberPreKeyId(request.getKyberPreKeyId());
		entity.setPublicKey(request.getPublicKey());
		entity.setSignature(request.getSignature());

		repository.save(entity);
		return KyberPreKeyMapper.toResponse(entity);
	}
}