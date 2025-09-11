package com.algomeet.authservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.algomeet.authservice.client.UserSecurityQuestionClient;
import com.algomeet.authservice.dto.UserSecurityQuestionRequest;
import com.algomeet.authservice.dto.UserSecurityQuestionResponse;

import feign.FeignException;

@Service
public class UserSecurityQuestionService {

	@Autowired
	private UserSecurityQuestionClient userSecurityQuestionAnswerClient;
	
    public UserSecurityQuestionResponse create(UserSecurityQuestionRequest request) {
        return userSecurityQuestionAnswerClient.create(request).getBody();
    }

    public List<UserSecurityQuestionResponse> getByUserProfileId(UUID userProfileId) {
        return userSecurityQuestionAnswerClient.getByUserProfileId(userProfileId).getBody();
    }

    public void deleteByUserProfileId(UUID userProfileId) {
        userSecurityQuestionAnswerClient.deleteByUserProfileId(userProfileId);
    }
    
    public UserSecurityQuestionResponse getByUserProfileIdAndQuestionId(
            UUID userProfileId,
            String securityQuestionId) {    
    	
    	try {
    		return userSecurityQuestionAnswerClient.getByUserProfileIdAndQuestionId(userProfileId, securityQuestionId).getBody();
    	} catch(FeignException ex) {
    		if(ex.status() != HttpStatus.NOT_FOUND.value()) {
    			throw ex;
    		}
    	}
    	
    	return null;
    }
    
    public UserSecurityQuestionResponse updateAnswer(
            UUID userProfileId,
            String securityQuestionId,
            UserSecurityQuestionRequest request) {
        return userSecurityQuestionAnswerClient.updateAnswer(userProfileId, securityQuestionId, request).getBody();
    }    
}