package com.algomeet.authservice.controller;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.authservice.controller.swagger.UserSecurityQuestionControllerDoc;
import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.UserSecurityQuestionRequest;
import com.algomeet.authservice.dto.UserSecurityQuestionResponse;
import com.algomeet.authservice.dto.VerifySecurityQuestionRequest;
import com.algomeet.authservice.dto.VerifySecurityQuestionResponse;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.service.UserSecurityQuestionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth/user-security-questions")
@RequiredArgsConstructor
public class UserSecurityQuestionController implements UserSecurityQuestionControllerDoc {
	
	private final UserSecurityQuestionService userSecurityQuestionService;

    @PostMapping
    public ResponseEntity<CommonResponse<UserSecurityQuestionResponse>> create(@Valid @RequestBody UserSecurityQuestionRequest request) {
    	if (Objects.nonNull(userSecurityQuestionService.getByUserProfileIdAndQuestionId(
    			UUID.fromString(request.getUserProfileId()), 
    			request.getSecurityQuestionId()))) {
    		// User security question id exists for the user profile id
    		return ResponseEntity.status(HttpStatus.CONFLICT).body(
    				CommonResponse.from(ResponseCode.USER_SECURITY_QUESTION_ID_EXISTS, 
    						null));
    	}
    	
    	UserSecurityQuestionResponse resp = userSecurityQuestionService.create(request);
    	// Mask answer before returning to client
		maskAnswer(resp);
    	
    	return ResponseEntity.ok(CommonResponse.from(ResponseCode.ADD_USER_SECURITY_QUESTION_SUCCESS, resp));
    }
    
    @PostMapping("/batch")
    public ResponseEntity<CommonResponse<List<UserSecurityQuestionResponse>>> create(@RequestBody List<UserSecurityQuestionRequest> requests) {
    	List<UserSecurityQuestionResponse> respList = new ArrayList<>();

		if (CollectionUtils.isEmpty(requests)) {
			return ResponseEntity.badRequest()
					.body(CommonResponse.from(ResponseCode.USER_SECURITY_QUESTION_VERIFY_FAILED, List.of()));
		}

		for (UserSecurityQuestionRequest r : requests) {
			if (Objects.nonNull(userSecurityQuestionService.getByUserProfileIdAndQuestionId(
					UUID.fromString(r.getUserProfileId()), r.getSecurityQuestionId()))) {
				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body(CommonResponse.from(ResponseCode.USER_SECURITY_QUESTION_ID_EXISTS, null));
			}
		}
		for (UserSecurityQuestionRequest request : requests) {
			UserSecurityQuestionResponse created = userSecurityQuestionService.create(request);
			maskAnswer(created);
			respList.add(created);
		}

        return ResponseEntity.ok(CommonResponse.from(ResponseCode.ADD_USER_SECURITY_QUESTION_SUCCESS, respList));
    }

    @GetMapping("/{userProfileId}")
    public ResponseEntity<CommonResponse<List<UserSecurityQuestionResponse>>> getByUserProfileId(@PathVariable UUID userProfileId) {
		List<UserSecurityQuestionResponse> respList = userSecurityQuestionService.getByUserProfileId(userProfileId);
		if (!CollectionUtils.isEmpty(respList)) {
			respList.forEach(UserSecurityQuestionController::maskAnswer);
		}

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, respList));
    }

    @DeleteMapping("/{userProfileId}")
    public ResponseEntity<CommonResponse<UserSecurityQuestionResponse>> deleteByUserProfileId(@PathVariable UUID userProfileId) {
    	userSecurityQuestionService.deleteByUserProfileId(userProfileId);    	
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.DELETE_USER_SECURITY_QUESTION_SUCCESS, null));
    }
    
    @GetMapping("/{userProfileId}/{securityQuestionId}")
    public ResponseEntity<CommonResponse<UserSecurityQuestionResponse>> getByUserProfileIdAndQuestionId(
            @PathVariable UUID userProfileId,
            @PathVariable String securityQuestionId) {
		UserSecurityQuestionResponse resp =
				userSecurityQuestionService.getByUserProfileIdAndQuestionId(userProfileId, securityQuestionId);

		if (resp == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.USER_SECURITY_QUESTION_VERIFY_FAILED, null));
		}

		maskAnswer(resp);
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, resp));
    }
    
    @PostMapping("/{userProfileId}/{securityQuestionId}/verify-answer")
    public ResponseEntity<?> verifyAnswer(
    		@PathVariable UUID userProfileId,
            @PathVariable String securityQuestionId,
    		@Valid @RequestBody VerifySecurityQuestionRequest request) {

		UserSecurityQuestionResponse saved =
				userSecurityQuestionService.getByUserProfileIdAndQuestionId(userProfileId, securityQuestionId);

		if (saved == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(
							ResponseCode.USER_SECURITY_QUESTION_VERIFY_FAILED,
							new VerifySecurityQuestionResponse(false, "Security question not found")));
		}

		boolean valid = saved.getAnswer() != null
				&& request.getAnswer() != null
				&& saved.getAnswer().equalsIgnoreCase(request.getAnswer().trim());

		return ResponseEntity.ok(CommonResponse.from(
				ResponseCode.SUCCESS,
				new VerifySecurityQuestionResponse(valid, valid ? "Answer is correct" : "Answer is incorrect")));
    }

	private static void maskAnswer(UserSecurityQuestionResponse resp) {
		if (resp != null) resp.setAnswer("***");
	}
}