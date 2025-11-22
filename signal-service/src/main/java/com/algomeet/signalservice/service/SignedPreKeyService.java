package com.algomeet.signalservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.signalservice.dto.SignedPreKeyRequest;
import com.algomeet.signalservice.dto.SignedPreKeyResponse;
import com.algomeet.signalservice.entity.SignedPreKey;
import com.algomeet.signalservice.entity.SignedPreKeyId;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.mapper.SignedPreKeyMapper;
import com.algomeet.signalservice.repository.SignedPreKeyRepository;

@Service
@Transactional
public class SignedPreKeyService {
    private final SignedPreKeyRepository repository;

    public SignedPreKeyService(SignedPreKeyRepository repository) {
        this.repository = repository;
    }

    public SignedPreKeyResponse getById(UUID userKey, Integer deviceId) {
    	SignedPreKeyId id = new SignedPreKeyId(userKey, deviceId);

    	return repository.findById(id).map(SignedPreKeyMapper::toResponse).orElseThrow(
    			() -> new RecordNotFoundException("Signed Pre Key not found"));
    }

    public SignedPreKeyResponse update(UUID userKey, Integer deviceId, SignedPreKeyRequest request) {
        SignedPreKeyId id = new SignedPreKeyId(userKey, deviceId);
        SignedPreKey entity = repository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Signed Pre Key not found"));

        // Update fields
        entity.setSignedPreKeyId(request.getSignedPreKeyId());
        entity.setPublicKey(request.getPublicKey());
        entity.setSignature(request.getSignature());

        repository.save(entity);
        return SignedPreKeyMapper.toResponse(entity);
    }

    public void delete(UUID userKey, Integer deviceId) {
    	SignedPreKeyId id = new SignedPreKeyId(userKey, deviceId);
        repository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Signed Pre Key not found"));

        repository.deleteById(id);
    }
}