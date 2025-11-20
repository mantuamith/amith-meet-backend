package com.algomeet.authservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.algomeet.authservice.client.UserSecurityQuestionClient;
import com.algomeet.authservice.dto.UserSecurityQuestionRequest;
import com.algomeet.authservice.dto.UserSecurityQuestionResponse;
import com.algomeet.authservice.util.SecurityQuestionSha256Util;

import feign.FeignException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserSecurityQuestionService {

	private final UserSecurityQuestionClient userSecurityQuestionAnswerClient;
	
    public UserSecurityQuestionResponse create(UserSecurityQuestionRequest request) {
    	try {
    		// Hash answer for better security
    		request.setAnswer(SecurityQuestionSha256Util.hashAnswer(request.getAnswer()));
    	} catch (Exception ex) {
    		throw new RuntimeException(ex);
    	}
    	    	
        return userSecurityQuestionAnswerClient.create(request).getBody();
    }

    public List<UserSecurityQuestionResponse> getByUserProfileId(UUID userProfileId) {
        return userSecurityQuestionAnswerClient.getByUserProfileId(userProfileId).getBody();
    }

    public void deleteByUserProfileId(UUID userProfileId) {
        try {
            userSecurityQuestionAnswerClient.deleteByUserProfileId(userProfileId);
        } catch (FeignException ex) {
            if (ex.status() != HttpStatus.NOT_FOUND.value()) {
                throw ex;
            }
        }
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
    	
    	try {
    		// Hash answer for better security
    		request.setAnswer(SecurityQuestionSha256Util.hashAnswer(request.getAnswer()));
    	} catch (Exception ex) {
    		throw new RuntimeException(ex);
    	}
    	
        return userSecurityQuestionAnswerClient.updateAnswer(userProfileId, securityQuestionId, request).getBody();
    }    
}