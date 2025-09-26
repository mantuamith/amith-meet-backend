package com.algomeet.authservice.service;

import java.util.UUID;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.algomeet.authservice.client.UserProfileClient;
import com.algomeet.authservice.dto.UserProfileResponse;
import com.algomeet.authservice.dto.UserProfileUpdateRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class UserProfileService {	
	private final UserProfileClient userProfileClient;	
	private final UserSecurityQuestionService userSecurityQuestionAnswerService;
	
	public UserProfileResponse findById(UUID id) {
		return userProfileClient.getProfile(id).getBody();
	}

	public UserProfileResponse updateProfile(UUID id, UserProfileUpdateRequest request) {
		try {
			ResponseEntity<UserProfileResponse> resp = userProfileClient.updateProfile(id, request);
			HttpStatusCode status = resp.getStatusCode();
			UserProfileResponse body = resp.getBody();

			if (status.is2xxSuccessful() && body != null) {
				// delete only if client EXPLICITLY asked to disable AND backend reflects disabled
				Boolean requested = request.getSecurityQuestionsEnabled();
				Boolean effective  = body.getSecurityQuestionsEnabled();
				if (Boolean.FALSE.equals(requested) && Boolean.FALSE.equals(effective)) {
					userSecurityQuestionAnswerService.deleteByUserProfileId(id);
				}
				return body;
			}

			if (status.is4xxClientError()) {
				throw new ResponseStatusException(status, "Invalid profile update request");
			}
			if (status.is5xxServerError()) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User service failed to process the request");
			}
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected response from user service");

		} catch (FeignException.NotFound e) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User profile not found", e);
		} catch (FeignException.BadRequest e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid profile update payload", e);
		} catch (FeignException e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream error calling user service (" + e.status() + ")", e);
		} catch (Exception e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update user profile", e);
		}
	}
}
