package com.algomeet.signalservice.mapper;

import java.util.UUID;

import com.algomeet.signalservice.dto.SignedPreKeyRequest;
import com.algomeet.signalservice.dto.SignedPreKeyResponse;
import com.algomeet.signalservice.entity.SignedPreKey;
import com.algomeet.signalservice.entity.SignedPreKeyId;

public class SignedPreKeyMapper {

    public static SignedPreKey toEntity(UUID userKey, Integer deviceId, SignedPreKeyRequest request) {
        SignedPreKey spk = new SignedPreKey();
        spk.setId(new SignedPreKeyId(userKey, deviceId));
        spk.setSignedPreKeyId(request.getSignedPreKeyId());
        spk.setPublicKey(request.getPublicKey());
        spk.setSignature(request.getSignature());
        return spk;
    }

    public static SignedPreKeyResponse toResponse(SignedPreKey entity) {
        SignedPreKeyResponse response = new SignedPreKeyResponse();
        response.setUserKey(entity.getId().getUserKey());
        response.setDeviceId(entity.getId().getDeviceId());
        response.setSignedPreKeyId(entity.getSignedPreKeyId());
        response.setPublicKey(entity.getPublicKey());
        response.setSignature(entity.getSignature());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}