package com.algomeet.signalingservice.service;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.algomeet.signalingservice.dto.UserOneTimeKeyRequest;
import com.algomeet.signalingservice.dto.UserOneTimeKeyResponse;
import com.algomeet.signalingservice.dto.UserIdentityAndOneTimeKeyResponse;
import com.algomeet.signalingservice.dto.UserIdentityKeyRequest;
import com.algomeet.signalingservice.dto.UserIdentityKeyResponse;
import com.algomeet.signalingservice.dto.UserKeysBackupRequest;
import com.algomeet.signalingservice.dto.UserKeysBackupResponse;
import com.algomeet.signalingservice.entity.UserOneTimeKey;
import com.algomeet.signalingservice.entity.UserIdentityKey;
import com.algomeet.signalingservice.entity.UserKeysBackup;
import com.algomeet.signalingservice.exceptions.IdentityKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.NoUserOneTimeKeyIsAvailableException;
import com.algomeet.signalingservice.exceptions.OneTimeKeyAlreadyExistsException;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.exceptions.UserKeyAlreadyExistsException;
import com.algomeet.signalingservice.repository.UserOneTimeKeyRepository;
import com.algomeet.signalingservice.util.GroupSessionUtil;
import com.algomeet.signalingservice.util.SessionUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.algomeet.signalingservice.repository.UserIdentityKeyRepository;
import com.algomeet.signalingservice.repository.UserKeysBackupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserKeyService {

    private final UserIdentityKeyRepository userIdentityRepo;
    private final UserOneTimeKeyRepository oneTimeRepo;
    private final UserKeysBackupRepository keyBackupRepo;

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
        List<UserOneTimeKey> oneTimeKeys = Optional.ofNullable(request.getOneTimeKeys()).orElse(List.of())
        		.stream()
        		.map(otk -> new UserOneTimeKey(userKey, otk))
        		.collect(Collectors.toList());
        userIdentityKey.setOneTimeKeys(oneTimeKeys);
        
        userIdentityKey = userIdentityRepo.save(userIdentityKey);

        return UserIdentityKeyResponse.builder()
                .userKey(userIdentityKey.getUserKey())
                .identityKey(userIdentityKey.getIdentityKey())
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
        
    public UserIdentityKeyResponse updateUserIdentity(UUID userKey, UserIdentityKeyRequest request) {
    	userIdentityRepo.findById(userKey).orElseThrow(() -> new RecordNotFoundException("User key not found"));
    	
    	// Clean up the old one-time-keys
    	//oneTimeRepo.deleteByUserKey(userKey);
    	
    	UserIdentityKey userIdentityKey = new UserIdentityKey();
        userIdentityKey.setUserKey(userKey);
        userIdentityKey.setIdentityKey(request.getIdentityKey());
        
        // Update identity table
        UserIdentityKey savedUserIdentityKey = userIdentityRepo.save(userIdentityKey); 
        
       	List<UserOneTimeKey> oneTimeKeys = Optional.ofNullable(request.getOneTimeKeys()).orElse(List.of())
        			.stream()
        			.map(otk -> new UserOneTimeKey(userKey, otk))
        			.collect(Collectors.toList());
       	
       	if (!CollectionUtils.isEmpty(oneTimeKeys)) {
       		List<UserOneTimeKey> savedOneTimeKeys = oneTimeRepo.saveAll(oneTimeKeys);
       		savedUserIdentityKey.setOneTimeKeys(savedOneTimeKeys);
       	}       	       	      

        return UserIdentityKeyResponse.builder()
                .userKey(savedUserIdentityKey.getUserKey())
                .identityKey(savedUserIdentityKey.getIdentityKey())
                .oneTimeKeys(Optional.ofNullable(savedUserIdentityKey.getOneTimeKeys()).orElse(List.of())
                		.stream()
                		.map(otk -> UserOneTimeKeyResponse.builder()
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

    public List<UserOneTimeKeyResponse> addOneTimeKeys(UUID userKey, UserOneTimeKeyRequest request) {
        UserIdentityKey userIdentity = userIdentityRepo.findById(userKey)
                .orElseThrow(() -> new RecordNotFoundException("User key not found"));
        
        
        List<UserOneTimeKey> onetimeKeys = new ArrayList<>();
        if (!CollectionUtils.isEmpty(userIdentity.getOneTimeKeys())) {
        	for(String oneTimeKey : request.getOneTimeKeys()) {
        		onetimeKeys.add(new UserOneTimeKey(userKey, oneTimeKey));
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
    	
    	Optional<UserOneTimeKey> oneTimeKeyOpt = oneTimeRepo.findFirstByUserKeyAndUsedFalse(userKey);
    	
    	Optional<UserOneTimeKeyResponse> optionalResponse = oneTimeKeyOpt    			
    			.map(k -> UserOneTimeKeyResponse.builder()
    					.id(k.getId())
    					.key(k.getOneTimeKey())
    					.build());

    	UserOneTimeKeyResponse oneTimeKey = optionalResponse
    			.orElseThrow(() -> new NoUserOneTimeKeyIsAvailableException("No user one time key is available"));
     	    	
    	// Update one time key "used" value to true
    	UserOneTimeKey usedOneTimeKey = oneTimeKeyOpt.get();
    	usedOneTimeKey.setUsed(true);
    	oneTimeRepo.save(usedOneTimeKey);
    	
    	return UserIdentityAndOneTimeKeyResponse.builder()
    			.userKey(userIdentityKey.getUserKey())
    			.identityKey(userIdentityKey.getIdentityKey())
    			.oneTimeKey(oneTimeKey)
    			.build();
    }
        
    public List<UserOneTimeKeyResponse> getOneTimeKeys(UUID userKey) {
    	userIdentityRepo.findById(userKey)
    			.orElseThrow(() -> new RecordNotFoundException("User key not found"));
        
    	return oneTimeRepo.findByUserKey(userKey)
                .stream()
                .map(k -> UserOneTimeKeyResponse.builder()
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
    
    public UserKeysBackupResponse createPrivateKeyBackup(UUID userKey, UserKeysBackupRequest request) throws JsonProcessingException, UnsupportedEncodingException {
    	UserKeysBackup keyBackup = new UserKeysBackup(userKey, 
    			request.getEncryptedAccount(),
    			SessionUtil.converToJson(request.getOutboundSessions()),
    			GroupSessionUtil.converToJson(request.getGroupSessions()));
    	
    	UserKeysBackup savedBackup = keyBackupRepo.save(keyBackup);
    	
    	return UserKeysBackupResponse.builder()
    			.userKey(savedBackup.getUserKey())
    			.encryptedAccount(savedBackup.getEncryptedAccount())
    			.outboundSessions(SessionUtil.converToObject(savedBackup.getOutboundSessions()))
    			.groupSessions(GroupSessionUtil.converToObject(savedBackup.getGroupSessions()))
    			.createdAt(savedBackup.getCreatedAt())
    			.updatedAt(savedBackup.getUpdatedAt())
    			.build();  
    }
    
    public UserKeysBackupResponse getPrivateKeyBackup(UUID userKey) throws JsonProcessingException, UnsupportedEncodingException {
    	UserKeysBackup userKeyBackup = keyBackupRepo.findById(userKey).orElseThrow(() -> new RecordNotFoundException("User key/ backup not found"));
    	
    	return UserKeysBackupResponse.builder()
    			.userKey(userKeyBackup.getUserKey())
    			.encryptedAccount(userKeyBackup.getEncryptedAccount())
    			.outboundSessions(SessionUtil.converToObject(userKeyBackup.getOutboundSessions()))
    			.groupSessions(GroupSessionUtil.converToObject(userKeyBackup.getGroupSessions()))
    			.createdAt(userKeyBackup.getCreatedAt())
    			.updatedAt(userKeyBackup.getUpdatedAt())
    			.build();  	
    }
}