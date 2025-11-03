package com.algomeet.signalingservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.algomeet.signalingservice.dto.UserIdentityAndOneTimeKeyResponse;
import com.algomeet.signalingservice.dto.UserIdentityAndOneTimeKeysResponse;
import com.algomeet.signalingservice.dto.UserIdentityKeyRequest;
import com.algomeet.signalingservice.dto.UserIdentityKeyResponse;
import com.algomeet.signalingservice.dto.UserOneTimeKeyRequest;
import com.algomeet.signalingservice.dto.UserOneTimeKeyResponse;
import com.algomeet.signalingservice.entity.UserIdentityKey;
import com.algomeet.signalingservice.entity.UserIdentityKeyId;
import com.algomeet.signalingservice.entity.UserOneTimeKey;
import com.algomeet.signalingservice.exceptions.IdentityKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.OneTimeKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.OneTimeKeysReservedMaxLimitExceededException;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.repository.UserIdentityKeyRepository;
import com.algomeet.signalingservice.repository.UserOneTimeKeyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserKeyService {

    private final UserIdentityKeyRepository userIdentityRepo;
    private final UserOneTimeKeyRepository oneTimeRepo;
    
    @Value("${one-time-keys.reserved-max-limit:5000}")
    private int reservedOneTimeKeysMaxLimit;

    public UserIdentityKeyResponse registerUserIdentity(UUID userKey, UserIdentityKeyRequest request) {
    	if (userIdentityRepo.findById(new UserIdentityKeyId(userKey, request.getIdentityKey())).isPresent()) {    		
    		throw new IdentityKeyAlreadyExistsException("User key and Identity key already exists");
    	}	    	
    	
        UserIdentityKey userIdentityKey = new UserIdentityKey();
        userIdentityKey.setId(new UserIdentityKeyId(userKey, request.getIdentityKey()));
        List<UserOneTimeKey> oneTimeKeys = Optional.ofNullable(request.getOneTimeKeys()).orElse(List.of())
        		.stream()
        		.map(otk -> new UserOneTimeKey(userKey, request.getIdentityKey(), otk))
        		.collect(Collectors.toList());
        userIdentityKey.setOneTimeKeys(oneTimeKeys);
        userIdentityKey.setDeviceId(request.getDeviceId());
        
        userIdentityKey = userIdentityRepo.save(userIdentityKey);

        return UserIdentityKeyResponse.builder()
                .userKey(userIdentityKey.getId().getUserKey())
                .identityKey(userIdentityKey.getId().getIdentityKey())
                .deviceId(userIdentityKey.getDeviceId())
                .oneTimeKeys(Optional.ofNullable(userIdentityKey.getOneTimeKeys()).orElse(List.of())
                		.stream()
                		.map(otk -> UserOneTimeKeyResponse.builder()
                				.id(otk.getId())
                				.key(otk.getOneTimeKey())
                				.createdAt(otk.getCreatedAt())
                				.build()).toList())
                .createdAt(userIdentityKey.getCreatedAt())
                .updatedAt(userIdentityKey.getUpdatedAt())
                .build();
    }
        
    public List<UserOneTimeKeyResponse> addOneTimeKeys(UUID userKey, String identityKey, UserOneTimeKeyRequest request) {
        UserIdentityKey userIdentity = userIdentityRepo.findById(new UserIdentityKeyId(userKey, identityKey))
                .orElseThrow(() -> new RecordNotFoundException("User key not found"));
        
        // Check if reserved keys max limit did not exceed
        List<UserOneTimeKey> allUserOnetimeKeys = oneTimeRepo.findByUserKey(userKey);         
        if (allUserOnetimeKeys != null && allUserOnetimeKeys.size() > reservedOneTimeKeysMaxLimit) {
        	throw new OneTimeKeysReservedMaxLimitExceededException("Number of user reserved one time keys max limit exceeded");
        }        
        
        List<UserOneTimeKey> onetimeKeys = new ArrayList<>();
        if (!CollectionUtils.isEmpty(userIdentity.getOneTimeKeys())) {
        	for(String oneTimeKey : request.getOneTimeKeys()) {
        		onetimeKeys.add(new UserOneTimeKey(userKey, identityKey, oneTimeKey));
        	}
        }

        // Check if one time keys already exist
        List<UserOneTimeKey> existingOneTimeKeys = oneTimeRepo.findByUserKeyAndOneTimeKeyIn(userKey, request.getOneTimeKeys());
        if (!CollectionUtils.isEmpty(existingOneTimeKeys)) {
        	
        	throw new OneTimeKeyAlreadyExistsException(existingOneTimeKeys.stream().map(otk -> otk.getOneTimeKey())
        			.collect(Collectors.joining(", ", "(", ")")));
        }
        
        List<UserOneTimeKey> saved = oneTimeRepo.saveAll(onetimeKeys);

        return saved.stream()
        	    .map(otk -> new UserOneTimeKeyResponse(otk.getId(), otk.getUserKey(), otk.getOneTimeKey()))
        	    .collect(Collectors.toList());
    }    
    
    public List<UserIdentityKeyResponse> getUserIdentityKeys(UUID userKey) {   	
    	List<UserIdentityKeyResponse> identityKeys = userIdentityRepo.findByIdUserKey(userKey)
    			.stream()
    			.map(k -> UserIdentityKeyResponse.builder()
    					.userKey(k.getId().getUserKey())
    					.identityKey(k.getId().getIdentityKey())    					
    					.createdAt(k.getCreatedAt())
    					.updatedAt(k.getUpdatedAt())
    					.build())
    			.toList();


    	return identityKeys;
    }
           
    public UserIdentityAndOneTimeKeysResponse getUserIdentityAndOneTimeKeys(UUID userKey) {
    	List<UserIdentityKey> userIdentityKeys = userIdentityRepo.findByIdUserKey(userKey);
    	
    	if (CollectionUtils.isEmpty(userIdentityKeys)) {
    		throw new RecordNotFoundException("User identity keys not found");
    	}
    	
    	UserIdentityAndOneTimeKeysResponse response = new UserIdentityAndOneTimeKeysResponse();
    	response.setUserKey(userKey);
    	response.setKeys(new ArrayList<>());
    	for (UserIdentityKey userIdentityKey : userIdentityKeys) {
    		Optional<UserOneTimeKey> oneTimeKeyOpt = oneTimeRepo.findFirstByUserKeyAndIdentityKeyAndUsedFalse(userKey, userIdentityKey.getId().getIdentityKey());

    		Optional<UserOneTimeKeyResponse> optionalResponse = oneTimeKeyOpt    			
    				.map(k -> UserOneTimeKeyResponse.builder()
    						.id(k.getId())
    						.userKey(k.getUserKey())
    						.identityKey(k.getIdentityKey())
    						.key(k.getOneTimeKey())
    						.used(k.isUsed())
    						.createdAt(k.getCreatedAt())
    						.build());

    		if(optionalResponse.isPresent()) {
    			UserOneTimeKeyResponse oneTimeKey = optionalResponse.get();

    			// Update one time key "used" value to true
    			UserOneTimeKey usedOneTimeKey = oneTimeKeyOpt.get();
    			usedOneTimeKey.setUsed(true);
    			oneTimeRepo.save(usedOneTimeKey);

    			response.getKeys().add(UserIdentityAndOneTimeKeyResponse.builder()
    					.deviceId(userIdentityKey.getDeviceId())
    					.identityKey(userIdentityKey.getId().getIdentityKey())
    					.oneTimeKey(oneTimeKey)
    					.build());
    		}
    	}
    	
    	return response;
    }
        
    public List<UserOneTimeKeyResponse> getOneTimeKeys(UUID userKey, String identityKey) {
    	userIdentityRepo.findById(new UserIdentityKeyId(userKey, identityKey))
    			.orElseThrow(() -> new RecordNotFoundException("User identity key not found"));
        
    	return oneTimeRepo.findByUserKey(userKey)
                .stream()
                .map(k -> UserOneTimeKeyResponse.builder()
                        .id(k.getId())
                        .key(k.getOneTimeKey())                        
                        .userKey(k.getUserKey()) 
                        .identityKey(k.getIdentityKey())
                        .used(k.isUsed())
                        .createdAt(k.getCreatedAt())
                        .updatedAt(k.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }
    
    public void deleteIdentityKey(UUID userKey, String identityKey) {
    	userIdentityRepo.findById(new UserIdentityKeyId(userKey, identityKey))
    	.orElseThrow(() -> new RecordNotFoundException("User identity key is not found"));   
    	
    	userIdentityRepo.deleteById(new UserIdentityKeyId(userKey, identityKey));
    	// Delete one time keys
    	oneTimeRepo.deleteByUserKeyAndIdentityKey(userKey, identityKey);
    }  
    
    public void deleteOneTimeKey(Long id, UUID userKey) {
    	oneTimeRepo.findById(id).orElseThrow(() -> new RecordNotFoundException("One time key ID is not found"));
    	oneTimeRepo.deleteByIdAndUserKeyOrUsed(id, userKey, true);
    }   
}