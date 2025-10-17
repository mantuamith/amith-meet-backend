package com.algomeet.signalingservice.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.algomeet.signalingservice.dto.IdentityOneTimeKeyRequest;
import com.algomeet.signalingservice.dto.IdentityOneTimeKeyResponse;
import com.algomeet.signalingservice.dto.UserIdentityAndOneTimeKeyResponse;
import com.algomeet.signalingservice.dto.UserIdentityKeyRequest;
import com.algomeet.signalingservice.dto.UserIdentityKeyResponse;
import com.algomeet.signalingservice.dto.UserPrivateKeyBackupRequest;
import com.algomeet.signalingservice.dto.UserPrivateKeyBackupResponse;
import com.algomeet.signalingservice.entity.IdentityOneTimeKey;
import com.algomeet.signalingservice.entity.UserIdentityKey;
import com.algomeet.signalingservice.entity.UserPrivateKeyBackup;
import com.algomeet.signalingservice.exceptions.IdentityKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.NoUserOneTimeKeyIsAvailableException;
import com.algomeet.signalingservice.exceptions.OneTimeKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.exceptions.UserKeyAlreadyExistsException;
import com.algomeet.signalingservice.repository.IdentityOneTimeKeyRepository;
import com.algomeet.signalingservice.repository.UserIdentityKeyRepository;
import com.algomeet.signalingservice.repository.UserPrivateKeyBackupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserIdentityKeyService {

    private final UserIdentityKeyRepository userIdentityRepo;
    private final IdentityOneTimeKeyRepository oneTimeRepo;
    private final UserPrivateKeyBackupRepository keyBackupRepo;

    public UserIdentityKeyResponse registerUserIdentity(UUID userKey, UserIdentityKeyRequest request) {
    	if (userIdentityRepo.findByIdentityKey(request.getIdentityKey()).isPresent()) {    		
    		throw new IdentityKeyAlreadyExistsException("Identity key already exists");
    	}
    	
    	if (userIdentityRepo.findById(userKey).isPresent()) {
    		throw new UserKeyAlreadyExistsException("User key already exists");	
    	}    	    	
    	
        UserIdentityKey userIdentityKey = new UserIdentityKey();
        userIdentityKey.setUserKey(userKey);
        userIdentityKey.setIdentityKey(request.getIdentityKey());
        List<IdentityOneTimeKey> oneTimeKeys = Optional.ofNullable(request.getOneTimeKeys()).orElse(List.of())
        		.stream()
        		.map(otk -> new IdentityOneTimeKey(userKey, otk))
        		.collect(Collectors.toList());
        userIdentityKey.setOneTimeKeys(oneTimeKeys);
        
        userIdentityKey = userIdentityRepo.save(userIdentityKey);

        return UserIdentityKeyResponse.builder()
                .userKey(userIdentityKey.getUserKey())
                .identityKey(userIdentityKey.getIdentityKey())
                .oneTimeKeys(Optional.ofNullable(userIdentityKey.getOneTimeKeys()).orElse(List.of())
                		.stream()
                		.map(otk -> IdentityOneTimeKeyResponse.builder()
                				.id(otk.getId())
                				.key(otk.getOneTimeKey())
                				.createdAt(otk.getCreatedAt())
                				.build()).toList())
                .createdAt(userIdentityKey.getCreatedAt())
                .updatedAt(userIdentityKey.getUpdatedAt())
                .build();
    }
        
    public UserIdentityKeyResponse updateUserIdentity(UUID userKey, UserIdentityKeyRequest request) {
    	userIdentityRepo.findById(userKey).orElseThrow(() -> new RecordNotFoundException("User key not found"));
    	
    	// Clean up the old one-time-keys
    	//oneTimeRepo.deleteByUserKey(userKey);
    	
    	UserIdentityKey userIdentityKey = new UserIdentityKey();
        userIdentityKey.setUserKey(userKey);
        userIdentityKey.setIdentityKey(request.getIdentityKey());
        
        // Update identity table
        UserIdentityKey savedUserIdentityKey = userIdentityRepo.save(userIdentityKey); 
        
       	List<IdentityOneTimeKey> oneTimeKeys = Optional.ofNullable(request.getOneTimeKeys()).orElse(List.of())
        			.stream()
        			.map(otk -> new IdentityOneTimeKey(userKey, otk))
        			.collect(Collectors.toList());
       	
       	if (!CollectionUtils.isEmpty(oneTimeKeys)) {
       		List<IdentityOneTimeKey> savedOneTimeKeys = oneTimeRepo.saveAll(oneTimeKeys);
       		savedUserIdentityKey.setOneTimeKeys(savedOneTimeKeys);
       	}       	       	      

        return UserIdentityKeyResponse.builder()
                .userKey(savedUserIdentityKey.getUserKey())
                .identityKey(savedUserIdentityKey.getIdentityKey())
                .oneTimeKeys(Optional.ofNullable(savedUserIdentityKey.getOneTimeKeys()).orElse(List.of())
                		.stream()
                		.map(otk -> IdentityOneTimeKeyResponse.builder()
                				.id(otk.getId())
                				.userKey(otk.getUserKey())
                				.key(otk.getOneTimeKey())
                				.createdAt(otk.getCreatedAt())
                				.updatedAt(otk.getUpdatedAt())
                				.build()).toList())
                .createdAt(savedUserIdentityKey.getCreatedAt())
                .updatedAt(savedUserIdentityKey.getUpdatedAt())
                .build();
    }

    public List<IdentityOneTimeKeyResponse> addOneTimeKeys(UUID userKey, IdentityOneTimeKeyRequest request) {
        UserIdentityKey userIdentity = userIdentityRepo.findById(userKey)
                .orElseThrow(() -> new RecordNotFoundException("User key not found"));
        
        
        List<IdentityOneTimeKey> onetimeKeys = new ArrayList<>();
        if (!CollectionUtils.isEmpty(userIdentity.getOneTimeKeys())) {
        	for(String oneTimeKey : request.getOneTimeKeys()) {
        		onetimeKeys.add(new IdentityOneTimeKey(userKey, oneTimeKey));
        	}
        }

        // Check if one time keys already exist
        List<IdentityOneTimeKey> existingOneTimeKeys = oneTimeRepo.findByUserKeyAndOneTimeKeyIn(userKey, request.getOneTimeKeys());
        if (!CollectionUtils.isEmpty(existingOneTimeKeys)) {
        	
        	throw new OneTimeKeyAlreadyExistsException(existingOneTimeKeys.stream().map(otk -> otk.getOneTimeKey())
        			.collect(Collectors.joining(", ", "(", ")")));
        }
        
        List<IdentityOneTimeKey> saved = oneTimeRepo.saveAll(onetimeKeys);

        return saved.stream()
        	    .map(otk -> new IdentityOneTimeKeyResponse(otk.getId(), otk.getUserKey(), otk.getOneTimeKey()))
        	    .collect(Collectors.toList());
    }    
    
    public UserIdentityKeyResponse getUserIdentityKey(UUID userKey) {   	
    	Optional<UserIdentityKeyResponse> identityKeyOpt = userIdentityRepo.findById(userKey)
    			.map(k -> UserIdentityKeyResponse.builder()
    					.userKey(k.getUserKey())
    					.identityKey(k.getIdentityKey())
    					.createdAt(k.getCreatedAt())
    					.updatedAt(k.getUpdatedAt())
    					.build());


    	return identityKeyOpt.orElseThrow(
    			() -> new RecordNotFoundException("User key not found"));
    }
           
    public UserIdentityAndOneTimeKeyResponse getUserIdentityAndOneTimeKey(UUID userKey) {
    	UserIdentityKey userIdentityKey = userIdentityRepo.findById(userKey).orElseThrow(() -> new RecordNotFoundException("User key not found"));
    	
    	Optional<IdentityOneTimeKey> oneTimeKeyOpt = oneTimeRepo.findFirstByUserKeyAndUsedFalse(userKey);
    	
    	Optional<IdentityOneTimeKeyResponse> optionalResponse = oneTimeKeyOpt    			
    			.map(k -> IdentityOneTimeKeyResponse.builder()
    					.id(k.getId())
    					.key(k.getOneTimeKey())
    					.build());

    	IdentityOneTimeKeyResponse oneTimeKey = optionalResponse
    			.orElseThrow(() -> new NoUserOneTimeKeyIsAvailableException("No user one time key is available"));
     	    	
    	// Update one time key "used" value to true
    	IdentityOneTimeKey usedOneTimeKey = oneTimeKeyOpt.get();
    	usedOneTimeKey.setUsed(true);
    	oneTimeRepo.save(usedOneTimeKey);
    	
    	return UserIdentityAndOneTimeKeyResponse.builder()
    			.userKey(userIdentityKey.getUserKey())
    			.identityKey(userIdentityKey.getIdentityKey())
    			.oneTimeKey(oneTimeKey)
    			.build();
    }
        
    public List<IdentityOneTimeKeyResponse> getOneTimeKeys(UUID userKey) {
    	userIdentityRepo.findById(userKey)
    			.orElseThrow(() -> new RecordNotFoundException("User key not found"));
        
    	return oneTimeRepo.findByUserKey(userKey)
                .stream()
                .map(k -> IdentityOneTimeKeyResponse.builder()
                        .id(k.getId())
                        .key(k.getOneTimeKey())
                        .userKey(k.getUserKey())  
                        .used(k.isUsed())
                        .createdAt(k.getCreatedAt())
                        .updatedAt(k.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }
    
    public void deleteOneTimeKey(Long id, UUID userKey) {
    	oneTimeRepo.findById(id).orElseThrow(() -> new RecordNotFoundException("One time key ID is not found"));
    	oneTimeRepo.deleteByIdAndUserKeyOrUsed(id, userKey, true);
    }
    
    public void createPrivateKeyBackup(UUID userKey, UserPrivateKeyBackupRequest request) {
    	UserPrivateKeyBackup keyBackup = new UserPrivateKeyBackup(userKey, request.getEncryptedPrivateKey());
    	keyBackupRepo.save(keyBackup);
    }
    
    public UserPrivateKeyBackupResponse getPrivateKeyBackup(UUID userKey) {
    	Optional<UserPrivateKeyBackupResponse> userKeyBackupOpt = keyBackupRepo.findById(userKey).map(kb -> UserPrivateKeyBackupResponse.builder()
    			.userKey(kb.getUserKey())
    			.encryptedPrivateKey(kb.getEncryptedPrivateKey())
    			.createdAt(kb.getCreatedAt())
    			.updatedAt(kb.getUpdatedAt())
    			.build());
    			    	
    	return userKeyBackupOpt.orElseThrow(() -> new RecordNotFoundException("User key/ backup not found"));
    }
}