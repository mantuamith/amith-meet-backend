package com.algomeet.signalingservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.algomeet.signalingservice.dto.*;
import com.algomeet.signalingservice.entity.*;
import com.algomeet.signalingservice.repository.*;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserIdentityKeyService {

    private final UserIdentityKeyRepository userRepo;
    private final IdentityOneTimeKeyRepository oneTimeRepo;

    public UserIdentityKeyResponse registerUserIdentity(UserIdentityKeyRequest request) {
        UserIdentityKey user = new UserIdentityKey();
        user.setUserKey(UUID.randomUUID());
        user.setIdentityKey(request.getIdentityKey());
        List<IdentityOneTimeKey> oneTimeKeys = Optional.ofNullable(request.getOneTimeKeys()).orElse(List.of())
        		.stream()
        		.map(otk -> new IdentityOneTimeKey(request.getIdentityKey(), otk))
        		.collect(Collectors.toList());
        user.setOneTimeKeys(oneTimeKeys);
        
        userRepo.save(user);

        return UserIdentityKeyResponse.builder()
                .userKey(user.getUserKey())
                .identityKey(user.getIdentityKey())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public List<IdentityOneTimeKeyResponse> addOneTimeKeys(IdentityOneTimeKeyRequest request) {
        UserIdentityKey user = userRepo.findByIdentityKey(request.getIdentityKey())
                .orElseThrow(() -> new IllegalArgumentException("User identity not found"));
        
        List<IdentityOneTimeKey> onetimeKeys = new ArrayList<>();
        if (!CollectionUtils.isEmpty(user.getOneTimeKeys())) {
        	for(String oneTimeKey : request.getOneTimeKeys()) {
        		onetimeKeys.add(new IdentityOneTimeKey(request.getIdentityKey(), oneTimeKey));
        	}
        }

        List<IdentityOneTimeKey> saved = oneTimeRepo.saveAll(onetimeKeys);

        return saved.stream()
        	    .map(otk -> new IdentityOneTimeKeyResponse(otk.getId(), otk.getIdentityKey(), otk.getOneTimeKey()))
        	    .collect(Collectors.toList());
    }

    public List<IdentityOneTimeKeyResponse> getUserIdentityOneTimeKeys(UUID userKey) {
    	UserIdentityKey identityKey = userRepo.findById(userKey).orElseThrow(() -> new RuntimeException("User key not found"));
        
    	return oneTimeRepo.findByIdentityKey(identityKey.getIdentityKey())
                .stream()
                .map(k -> IdentityOneTimeKeyResponse.builder()
                        .id(k.getId())
                        .identityKey(k.getIdentityKey())
                        .createdAt(k.getCreatedAt())
                        .updatedAt(k.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }
    
    public IdentityOneTimeKeyResponse getUserIdentityAndOneTimeKey(UUID userKey) {
    	UserIdentityKey identityKey = userRepo.findById(userKey).orElseThrow(() -> new RuntimeException("User key not found"));
    	List<IdentityOneTimeKeyResponse> oneTimeKeys = oneTimeRepo.findByIdentityKey(identityKey.getIdentityKey())
                .stream()
                .map(k -> IdentityOneTimeKeyResponse.builder()
                        .id(k.getId())
                        .identityKey(k.getIdentityKey())
                        .createdAt(k.getCreatedAt())
                        .updatedAt(k.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    	
    	if (CollectionUtils.isEmpty(oneTimeKeys)) {
    		return null;
    	}
    	
    	return oneTimeKeys.get(0);
    }
    
    public void deleteOneTimeKey(Long id) {
    	oneTimeRepo.deleteById(id);
    }
}