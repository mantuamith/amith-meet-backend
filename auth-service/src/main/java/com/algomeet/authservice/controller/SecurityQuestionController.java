package com.algomeet.authservice.controller;

import java.util.List;
import java.util.Objects;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.authservice.controller.swagger.SecurityQuestionControllerDoc;
import com.algomeet.authservice.dto.CommonResponse;
import com.algomeet.authservice.dto.SecurityQuestionRequest;
import com.algomeet.authservice.dto.SecurityQuestionResponse;
import com.algomeet.authservice.enums.ResponseCode;
import com.algomeet.authservice.service.SecurityQuestionService;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
@RestController
@RequestMapping("/auth/security-questions")
@SecurityRequirement(name = "bearerAuth")
public class SecurityQuestionController implements SecurityQuestionControllerDoc{

	private final SecurityQuestionService securityQuestionService;
	
    // Create
    @PostMapping
    @PreAuthorize("hasAnyRole('SA','ADMIN')")
    public ResponseEntity<CommonResponse<SecurityQuestionResponse>> create(@Valid @RequestBody SecurityQuestionRequest request) {
    	if(Objects.nonNull(securityQuestionService.getById(request.getId()))) {
    		// Id exist
    		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResponse.from(ResponseCode.SECURITY_QUESTION_ID_EXISTS, 
    				null));
    	}    	
    	
       return ResponseEntity.ok(CommonResponse.from(ResponseCode.ADD_SECURITY_QUESTION_SUCCESS, 
        		securityQuestionService.create(request)));
    }

    // Get by ID
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponse<SecurityQuestionResponse>> getById(@PathVariable String id) {
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
        		securityQuestionService.getById(id)));
    }

    // Get all
    @GetMapping
    public ResponseEntity<CommonResponse<List<SecurityQuestionResponse>>> getAll() {
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
        		securityQuestionService.getAll()));
    }


    // Update (PUT = full replace)
    @PreAuthorize("hasAnyRole('SA','ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponse<SecurityQuestionResponse>> update(
            @PathVariable String id,
            @RequestBody SecurityQuestionRequest request) {
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.UPDATE_SECURITY_QUESTION_SUCCESS, 
        		securityQuestionService.update(id, request)));
    }

    // Delete
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponse<?>> delete(@PathVariable String id) {  
    	securityQuestionService.delete(id);
    	return ResponseEntity.ok(CommonResponse.from(ResponseCode.DELETE_SECURITY_QUESTION_SUCCESS, null));
    }
}