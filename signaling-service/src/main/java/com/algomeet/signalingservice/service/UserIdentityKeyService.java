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
import com.algomeet.signalingservice.exceptions.IdentityKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.exceptions.UserDontHaveOneTimeKeyAvailableException;
import com.algomeet.signalingservice.exceptions.UserKeyAlreadyExistsException;
import com.algomeet.signalingservice.repository.*;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserIdentityKeyService {

    private final UserIdentityKeyRepository userRepo;
    private final IdentityOneTimeKeyRepository oneTimeRepo;

    public UserIdentityKeyResponse registerUserIdentity(UUID userKey, UserIdentityKeyRequest request) {
    	if (userRepo.findById(userKey).isPresent()) {
    		throw new UserKeyAlreadyExistsException("User key already exists");	
    	}
    	
    	if (userRepo.findByIdentityKey(request.getIdentityKey()).isPresent()) {    		
    		throw new IdentityKeyAlreadyExistsException("Identity key already exists");
    	}
    	
        UserIdentityKey userIdentityKey = new UserIdentityKey();
        userIdentityKey.setUserKey(userKey);
        userIdentityKey.setIdentityKey(request.getIdentityKey());
        List<IdentityOneTimeKey> oneTimeKeys = Optional.ofNullable(request.getOneTimeKeys()).orElse(List.of())
        		.stream()
        		.map(otk -> new IdentityOneTimeKey(userKey, otk))
        		.collect(Collectors.toList());
        userIdentityKey.setOneTimeKeys(oneTimeKeys);
        
        userIdentityKey = userRepo.save(userIdentityKey);

        return UserIdentityKeyResponse.builder()
                .userKey(userIdentityKey.getUserKey().toString())
                .identityKey(userIdentityKey.getIdentityKey())
                .oneTimeKeys(Optional.ofNullable(userIdentityKey.getOneTimeKeys()).orElse(List.of())
                		.stream()
                		.map(otk -> IdentityOneTimeKeyResponse.builder()
                				.id(otk.getId())
                				.oneTimekey(otk.getOneTimeKey())
                				.createdAt(otk.getCreatedAt())
                				.build()).toList())
                .createdAt(userIdentityKey.getCreatedAt())
                .updatedAt(userIdentityKey.getUpdatedAt())
                .build();
    }
        
    public UserIdentityKeyResponse updateUserIdentity(UUID userKey, UserIdentityKeyRequest request) {
    	UserIdentityKey userIdentityKey = userRepo.findById(userKey).orElseThrow(() -> new RecordNotFoundException("User key not found"));
    	
        userIdentityKey.setUserKey(userKey);
        userIdentityKey.setIdentityKey(request.getIdentityKey());
        List<IdentityOneTimeKey> oneTimeKeys = Optional.ofNullable(request.getOneTimeKeys()).orElse(List.of())
        		.stream()
        		.map(otk -> new IdentityOneTimeKey(userKey, otk))
        		.collect(Collectors.toList());
        userIdentityKey.setOneTimeKeys(oneTimeKeys);
        
        userIdentityKey = userRepo.save(userIdentityKey);

        return UserIdentityKeyResponse.builder()
                .userKey(userIdentityKey.getUserKey().toString())
                .identityKey(userIdentityKey.getIdentityKey())
                .oneTimeKeys(Optional.ofNullable(userIdentityKey.getOneTimeKeys()).orElse(List.of())
                		.stream()
                		.map(otk -> IdentityOneTimeKeyResponse.builder()
                				.id(otk.getId())
                				.oneTimekey(otk.getOneTimeKey())
                				.createdAt(otk.getCreatedAt())
                				.build()).toList())
                .createdAt(userIdentityKey.getCreatedAt())
                .updatedAt(userIdentityKey.getUpdatedAt())
                .build();
    }

    public List<IdentityOneTimeKeyResponse> addOneTimeKeys(UUID userKey, IdentityOneTimeKeyRequest request) {
        UserIdentityKey userIdentity = userRepo.findById(userKey)
                .orElseThrow(() -> new RecordNotFoundException("User key not found"));
        
        List<IdentityOneTimeKey> onetimeKeys = new ArrayList<>();
        if (!CollectionUtils.isEmpty(userIdentity.getOneTimeKeys())) {
        	for(String oneTimeKey : request.getOneTimeKeys()) {
        		onetimeKeys.add(new IdentityOneTimeKey(userKey, oneTimeKey));
        	}
        }

        List<IdentityOneTimeKey> saved = oneTimeRepo.saveAll(onetimeKeys);

        return saved.stream()
        	    .map(otk -> new IdentityOneTimeKeyResponse(otk.getId(), otk.getUserKey(), otk.getOneTimeKey()))
        	    .collect(Collectors.toList());
    }    
    
    public UserIdentityKeyResponse getUserIdentityKey(UUID userKey) {   	
    	Optional<UserIdentityKeyResponse> identityKeyOpt = userRepo.findById(userKey)
    			.map(k -> UserIdentityKeyResponse.builder()
    					.userKey(k.getUserKey().toString())
    					.identityKey(k.getIdentityKey())
    					.createdAt(k.getCreatedAt())
    					.updatedAt(k.getUpdatedAt())
    					.build());


    	return identityKeyOpt.orElseThrow(
    			() -> new RecordNotFoundException("User key not found"));
    }
           
    public IdentityOneTimeKeyResponse getUserIdentityAndOneTimeKey(UUID userKey) {
    	UserIdentityKey identityKey = userRepo.findById(userKey).orElseThrow(() -> new RecordNotFoundException("User key not found"));
    	
    	List<IdentityOneTimeKeyResponse> oneTimeKeys = oneTimeRepo.findByIdentityKeyAndUsedFalse(identityKey.getIdentityKey())
                .stream()
                .map(k -> IdentityOneTimeKeyResponse.builder()
                        .id(k.getId())
                        .oneTimekey(k.getOneTimeKey())
                        .userKey(k.getUserKey())
                        .createdAt(k.getCreatedAt())
                        .updatedAt(k.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    	
    	if (CollectionUtils.isEmpty(oneTimeKeys)) {
    		throw new UserDontHaveOneTimeKeyAvailableException("User dont have one time key available");
    	}
    	    	
    	// Update one time key used status to true
    	IdentityOneTimeKey usedOneTimeKey = new IdentityOneTimeKey();
    	usedOneTimeKey.setId(oneTimeKeys.get(0).getId());
    	usedOneTimeKey.setUsed(true);
    	oneTimeRepo.save(usedOneTimeKey);
    	
    	return oneTimeKeys.get(0);
    }
    
    public void deleteOneTimeKey(Long id) {
    	oneTimeRepo.findById(id).orElseThrow(() -> new RecordNotFoundException("One time key ID not found"));
    	oneTimeRepo.deleteById(id);
    }
    
    public List<IdentityOneTimeKeyResponse> getOneTimeKeys(UUID userKey) {
    	UserIdentityKey userIdentityKey = userRepo.findById(userKey)
    			.orElseThrow(() -> new RecordNotFoundException("User key not found"));
        
    	return oneTimeRepo.findByIdentityKey(userIdentityKey.getIdentityKey())
                .stream()
                .map(k -> IdentityOneTimeKeyResponse.builder()
                        .id(k.getId())
                        .oneTimekey(k.getOneTimeKey())
                        .userKey(k.getUserKey())
                        .createdAt(k.getCreatedAt())
                        .updatedAt(k.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}