package com.algomeet.authservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.authservice.client.UserProfileClient;
import com.algomeet.authservice.dto.UserProfileResponse;
import com.algomeet.authservice.dto.UserProfileUpdateRequest;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UserProfileService {	
	private final UserProfileClient userProfileClient;	
	private final UserSecurityQuestionService userSecurityQuestionAnswerService;
	
	public UserProfileResponse findById(UUID id) {
		return userProfileClient.getProfile(id).getBody();
	}
	
	public UserProfileResponse updateProfile(UUID id, UserProfileUpdateRequest request) {
	
		UserProfileResponse updateResp = userProfileClient.updateProfile(id, request).getBody();
		if ((request.getSecurityQuestionsEnabled() != null 
				&& updateResp.getSecurityQuestionsEnabled() != null)
				&& updateResp.getSecurityQuestionsEnabled() == false) {
			
			// Remove security question answers when disabled
			userSecurityQuestionAnswerService.deleteByUserProfileId(id);
		}
		
		return updateResp;
	}
}
